package edu.udeo.horarios.api.importing;

import edu.udeo.horarios.api.catalog.common.BadRequestException;
import edu.udeo.horarios.api.catalog.common.PageResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AcademicImportService {
  private static final List<String> REQUIRED_SHEETS =
      List.of(
          "careers",
          "curricula",
          "courses",
          "curriculum_courses",
          "cohorts",
          "teachers",
          "teacher_courses",
          "teacher_availability",
          "rooms",
          "journeys",
          "fixed_breaks",
          "common_areas",
          "common_area_careers");

  private static final Map<String, List<String>> REQUIRED_COLUMNS =
      Map.ofEntries(
          Map.entry("careers", List.of("code", "name", "active")),
          Map.entry("curricula", List.of("code", "career_code", "year", "is_active")),
          Map.entry(
              "courses",
              List.of("code", "name", "requires_lab", "weekly_blocks_min", "weekly_blocks_max")),
          Map.entry(
              "curriculum_courses",
              List.of("career_code", "curriculum_code", "course_code", "semester_number")),
          Map.entry(
              "cohorts",
              List.of(
                  "career_code",
                  "curriculum_code",
                  "semester_number",
                  "section",
                  "journey_code",
                  "expected_students",
                  "active")),
          Map.entry(
              "teachers",
              List.of("code", "full_name", "priority", "min_courses", "max_courses", "active")),
          Map.entry("teacher_courses", List.of("teacher_code", "course_code")),
          Map.entry(
              "teacher_availability",
              List.of("teacher_code", "day_of_week", "start_block", "duration_blocks")),
          Map.entry("rooms", List.of("code", "capacity", "type", "floor", "number", "active")),
          Map.entry(
              "journeys", List.of("code", "name", "block_minutes", "start_time", "end_time")),
          Map.entry("fixed_breaks", List.of("journey_code", "day_of_week", "start_block", "duration_blocks")),
          Map.entry(
              "common_areas", List.of("code", "course_code", "journey_code", "semester_number")),
          Map.entry("common_area_careers", List.of("common_area_code", "career_code", "curriculum_code")));

  private final JdbcTemplate jdbc;
  private final JdbcClient client;

  AcademicImportService(JdbcTemplate jdbc, JdbcClient client) {
    this.jdbc = jdbc;
    this.client = client;
  }

  @Transactional
  ImportResponse importAcademicData(MultipartFile file, ImportMode mode) throws IOException {
    String filename = Objects.requireNonNullElse(file.getOriginalFilename(), "academic-data");
    long userId = systemUserId();
    long batchId = createBatch(userId, filename);
    ImportFile parsed = parse(file);
    List<ImportError> errors = validate(parsed);
    int rowsRead = parsed.rows().values().stream().mapToInt(List::size).sum();
    int rowsInvalid = (int) errors.stream().map(error -> error.sheetName + ":" + error.rowNumber).distinct().count();
    int rowsValid = Math.max(0, rowsRead - rowsInvalid);
    String status = errors.isEmpty() ? (mode == ImportMode.IMPORT ? "IMPORTED" : "VALID") : "INVALID";

    if (errors.isEmpty() && mode == ImportMode.IMPORT) {
      persist(parsed);
    } else {
      saveErrors(batchId, errors);
    }
    Map<String, Integer> summary =
        Map.of("rowsRead", rowsRead, "rowsValid", rowsValid, "rowsInvalid", rowsInvalid);
    finishBatch(batchId, status, summary);
    return new ImportResponse(batchId, status, filename, summary, errors.size());
  }

  PageResponse<ImportErrorResponse> errors(long id, int page, int size, String sheetName) {
    if (page < 0 || size < 1 || size > 100) {
      throw new BadRequestException("Paginacion invalida.");
    }
    String where = sheetName == null || sheetName.isBlank() ? "" : " and sheet_name = ?";
    List<Object> args = new ArrayList<>();
    args.add(id);
    if (!where.isEmpty()) {
      args.add(sheetName);
    }
    Long total =
        jdbc.queryForObject(
            "select count(*) from import_error where import_batch_id = ?" + where,
            Long.class,
            args.toArray());
    args.add(size);
    args.add(page * size);
    List<ImportErrorResponse> items =
        jdbc.query(
            """
            select id, sheet_name, row_number, column_name, raw_value, code, message, suggested_action
            from import_error
            where import_batch_id = ?
            """
                + where
                + " order by id limit ? offset ?",
            (rs, rowNum) -> errorResponse(rs),
            args.toArray());
    long count = total == null ? 0 : total;
    return new PageResponse<>(items, page, size, count, (int) Math.ceil((double) count / size));
  }

  private ImportFile parse(MultipartFile file) throws IOException {
    String name = Objects.requireNonNullElse(file.getOriginalFilename(), "").toLowerCase(Locale.ROOT);
    if (name.endsWith(".xlsx")) {
      return parseXlsx(file.getInputStream());
    }
    if (name.endsWith(".csv")) {
      return parseCsv(file.getInputStream());
    }
    throw new BadRequestException("Solo se acepta CSV o XLSX.");
  }

  private ImportFile parseXlsx(InputStream input) throws IOException {
    try (var workbook = WorkbookFactory.create(input)) {
      DataFormatter formatter = new DataFormatter();
      Map<String, List<ImportRow>> rows = emptyRows();
      Map<String, Set<String>> columns = new HashMap<>();
      for (var sheet : workbook) {
        String sheetName = normalize(sheet.getSheetName());
        if (!REQUIRED_SHEETS.contains(sheetName)) {
          continue;
        }
        Row header = sheet.getRow(0);
        if (header == null) {
          columns.put(sheetName, Set.of());
          continue;
        }
        List<String> headers = headers(header, formatter);
        columns.put(sheetName, new HashSet<>(headers));
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
          Row row = sheet.getRow(i);
          if (row == null) {
            continue;
          }
          Map<String, String> values = values(row, headers, formatter);
          if (!values.values().stream().allMatch(String::isBlank)) {
            rows.get(sheetName).add(new ImportRow(sheetName, i + 1, values));
          }
        }
      }
      return new ImportFile(rows, columns);
    } catch (Exception ex) {
      if (ex instanceof IOException io) {
        throw io;
      }
      throw new BadRequestException("XLSX invalido.");
    }
  }

  private ImportFile parseCsv(InputStream input) throws IOException {
    Map<String, List<ImportRow>> rows = emptyRows();
    Map<String, Set<String>> columns = new HashMap<>();
    try (var reader = new BufferedReader(new InputStreamReader(input))) {
      String first = reader.readLine();
      if (first == null) {
        return new ImportFile(rows, columns);
      }
      List<String> headers = splitCsv(first).stream().map(this::normalize).toList();
      if (!headers.contains("sheet")) {
        columns.put("csv", new HashSet<>(headers));
        return new ImportFile(rows, columns);
      }
      String line;
      int rowNumber = 1;
      while ((line = reader.readLine()) != null) {
        rowNumber++;
        List<String> parts = splitCsv(line);
        Map<String, String> values = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
          values.put(headers.get(i), i < parts.size() ? parts.get(i).trim() : "");
        }
        String sheet = normalize(values.remove("sheet"));
        if (REQUIRED_SHEETS.contains(sheet)) {
          columns.computeIfAbsent(sheet, ignored -> new HashSet<>()).addAll(values.keySet());
          rows.get(sheet).add(new ImportRow(sheet, rowNumber, values));
        }
      }
    }
    return new ImportFile(rows, columns);
  }

  private List<ImportError> validate(ImportFile file) {
    List<ImportError> errors = new ArrayList<>();
    for (String sheet : REQUIRED_SHEETS) {
      if (!file.columns().containsKey(sheet)) {
        errors.add(error(sheet, null, null, null, "MISSING_SHEET", "La hoja es obligatoria.", "Agregar la hoja."));
        continue;
      }
      for (String column : REQUIRED_COLUMNS.get(sheet)) {
        if (!file.columns().get(sheet).contains(column)) {
          errors.add(
              error(
                  sheet,
                  1,
                  column,
                  null,
                  "MISSING_COLUMN",
                  "La columna es obligatoria.",
                  "Agregar la columna."));
        }
      }
    }
    for (String sheet : REQUIRED_SHEETS) {
      validateRequiredCells(file.rows().get(sheet), REQUIRED_COLUMNS.get(sheet), errors);
    }
    validateFormats(file, errors);
    validateCodes(file, errors);
    return errors;
  }

  private void validateFormats(ImportFile file, List<ImportError> errors) {
    for (ImportRow row : file.rows().get("curricula")) {
      validateInt(row, "year", errors);
      validateBool(row, "is_active", errors);
      validateDate(row, "valid_from", errors);
      validateDate(row, "valid_until", errors);
    }
    for (ImportRow row : file.rows().get("courses")) {
      validateBool(row, "requires_lab", errors);
      validateInt(row, "weekly_blocks_min", errors);
      validateInt(row, "weekly_blocks_max", errors);
    }
    for (ImportRow row : file.rows().get("curriculum_courses")) {
      validateInt(row, "semester_number", errors);
    }
    for (ImportRow row : file.rows().get("cohorts")) {
      validateInt(row, "semester_number", errors);
      validateInt(row, "expected_students", errors);
      validateBool(row, "active", errors);
    }
    for (ImportRow row : file.rows().get("teachers")) {
      validateInt(row, "priority", errors);
      validateInt(row, "min_courses", errors);
      validateInt(row, "max_courses", errors);
      validateBool(row, "active", errors);
    }
    for (ImportRow row : file.rows().get("teacher_courses")) {
      validateInt(row, "preference", errors);
    }
    for (ImportRow row : file.rows().get("teacher_availability")) {
      validateInt(row, "day_of_week", errors);
      validateInt(row, "start_block", errors);
      validateInt(row, "duration_blocks", errors);
      validateInt(row, "preference", errors);
    }
    for (ImportRow row : file.rows().get("rooms")) {
      validateInt(row, "capacity", errors);
      validateInt(row, "floor", errors);
      validateInt(row, "number", errors);
      validateBool(row, "active", errors);
      if (!Set.of("THEORY", "LAB", "MIXED").contains(cell(row, "type"))) {
        errors.add(error(row.sheetName, row.rowNumber, "type", cell(row, "type"), "INVALID_VALUE", "Tipo de aula invalido.", "Usar THEORY, LAB o MIXED."));
      }
    }
    for (ImportRow row : file.rows().get("journeys")) {
      validateInt(row, "block_minutes", errors);
      validateTime(row, "start_time", errors);
      validateTime(row, "end_time", errors);
    }
    for (ImportRow row : file.rows().get("fixed_breaks")) {
      validateInt(row, "day_of_week", errors);
      validateInt(row, "start_block", errors);
      validateInt(row, "duration_blocks", errors);
    }
    for (ImportRow row : file.rows().get("common_areas")) {
      validateInt(row, "semester_number", errors);
      validateBool(row, "active", errors);
    }
  }

  private void validateCodes(ImportFile file, List<ImportError> errors) {
    checkUnique(file.rows().get("careers"), "code", "career", "career", errors);
    checkUnique(file.rows().get("courses"), "code", "course", "course", errors);
    checkUnique(file.rows().get("teachers"), "code", "teacher", "teacher", errors);
    checkUnique(file.rows().get("rooms"), "code", "room", "room", errors);
    checkUnique(file.rows().get("journeys"), "code", "journey", "journey", errors);
    checkUnique(file.rows().get("common_areas"), "code", "common_area_rule", "common_area", errors);

    Codes codes = codes(file);
    for (ImportRow row : file.rows().get("curricula")) {
      require(codes.careers, row, "career_code", "La carrera no existe.", errors);
    }
    for (ImportRow row : file.rows().get("curriculum_courses")) {
      require(codes.curricula, key(row, "career_code", "curriculum_code"), row, "curriculum_code", "El pensum no existe.", errors);
      require(codes.courses, row, "course_code", "El curso no existe.", errors);
    }
    for (ImportRow row : file.rows().get("cohorts")) {
      require(codes.careers, row, "career_code", "La carrera no existe.", errors);
      require(codes.curricula, key(row, "career_code", "curriculum_code"), row, "curriculum_code", "El pensum no existe.", errors);
      require(codes.journeys, row, "journey_code", "La jornada no existe.", errors);
    }
    for (ImportRow row : file.rows().get("teacher_courses")) {
      require(codes.teachers, row, "teacher_code", "El docente no existe.", errors);
      require(codes.courses, row, "course_code", "El curso no existe.", errors);
    }
    for (ImportRow row : file.rows().get("teacher_availability")) {
      require(codes.teachers, row, "teacher_code", "El docente no existe.", errors);
      if (!cell(row, "journey_code").isBlank()) {
        require(codes.journeys, row, "journey_code", "La jornada no existe.", errors);
      }
    }
    for (ImportRow row : file.rows().get("fixed_breaks")) {
      require(codes.journeys, row, "journey_code", "La jornada no existe.", errors);
    }
    for (ImportRow row : file.rows().get("common_areas")) {
      require(codes.courses, row, "course_code", "El curso no existe.", errors);
      require(codes.journeys, row, "journey_code", "La jornada no existe.", errors);
    }
    for (ImportRow row : file.rows().get("common_area_careers")) {
      require(codes.commonAreas, row, "common_area_code", "El area comun no existe.", errors);
      require(codes.careers, row, "career_code", "La carrera no existe.", errors);
      require(codes.curricula, key(row, "career_code", "curriculum_code"), row, "curriculum_code", "El pensum no existe.", errors);
    }
  }

  private void persist(ImportFile file) {
    Map<String, Long> careers = insertCodeRows(file.rows().get("careers"), "career", "insert into career(code,name,active) values (?,?,?) returning id", row -> new Object[] {cell(row, "code"), cell(row, "name"), bool(row, "active")});
    Map<String, Long> journeys = insertCodeRows(file.rows().get("journeys"), "journey", "insert into journey(code,name,block_minutes,start_time,end_time) values (?,?,?,?,?) returning id", row -> new Object[] {cell(row, "code"), cell(row, "name"), integer(row, "block_minutes"), time(row, "start_time"), time(row, "end_time")});
    Map<String, Long> courses = insertCodeRows(file.rows().get("courses"), "course", "insert into course(code,name,requires_lab,weekly_blocks_min,weekly_blocks_max) values (?,?,?,?,?) returning id", row -> new Object[] {cell(row, "code"), cell(row, "name"), bool(row, "requires_lab"), integer(row, "weekly_blocks_min"), integer(row, "weekly_blocks_max")});
    Map<String, Long> teachers = insertCodeRows(file.rows().get("teachers"), "teacher", "insert into teacher(code,full_name,priority,min_courses,max_courses,active) values (?,?,?,?,?,?) returning id", row -> new Object[] {cell(row, "code"), cell(row, "full_name"), integer(row, "priority"), integer(row, "min_courses"), integer(row, "max_courses"), bool(row, "active")});
    insertCodeRows(file.rows().get("rooms"), "room", "insert into room(code,capacity,type,floor,number,active) values (?,?,?::room_type,?,?,?) returning id", row -> new Object[] {cell(row, "code"), integer(row, "capacity"), cell(row, "type"), integer(row, "floor"), integer(row, "number"), bool(row, "active")});

    Map<String, Long> curricula = new HashMap<>();
    for (ImportRow row : file.rows().get("curricula")) {
      Long id =
          insert(
              "insert into curriculum(career_id,code,year,is_active,valid_from,valid_until) values (?,?,?,?,?,?) returning id",
              careers.get(cell(row, "career_code")),
              cell(row, "code"),
              integer(row, "year"),
              bool(row, "is_active"),
              dateOrNull(row, "valid_from"),
              dateOrNull(row, "valid_until"));
      curricula.put(key(row, "career_code", "code"), id);
    }
    for (ImportRow row : file.rows().get("curriculum_courses")) {
      jdbc.update(
          "insert into curriculum_course(curriculum_id,course_id,semester_number) values (?,?,?)",
          curricula.get(key(row, "career_code", "curriculum_code")),
          courses.get(cell(row, "course_code")),
          integer(row, "semester_number"));
    }
    for (ImportRow row : file.rows().get("cohorts")) {
      jdbc.update(
          """
          insert into cohort(career_id,curriculum_id,semester_number,section,journey_id,expected_students,active)
          values (?,?,?,?,?,?,?)
          """,
          careers.get(cell(row, "career_code")),
          curricula.get(key(row, "career_code", "curriculum_code")),
          integer(row, "semester_number"),
          cell(row, "section"),
          journeys.get(cell(row, "journey_code")),
          integer(row, "expected_students"),
          bool(row, "active"));
    }
    for (ImportRow row : file.rows().get("teacher_courses")) {
      jdbc.update(
          "insert into teacher_course(teacher_id,course_id,preference) values (?,?,?)",
          teachers.get(cell(row, "teacher_code")),
          courses.get(cell(row, "course_code")),
          integerOrDefault(row, "preference", 0));
    }
    for (ImportRow row : file.rows().get("teacher_availability")) {
      jdbc.update(
          """
          insert into teacher_availability(teacher_id,journey_id,day_of_week,start_block,duration_blocks,preference,source)
          values (?,?,?,?,?,?,?)
          """,
          teachers.get(cell(row, "teacher_code")),
          cell(row, "journey_code").isBlank() ? null : journeys.get(cell(row, "journey_code")),
          integer(row, "day_of_week"),
          integer(row, "start_block"),
          integer(row, "duration_blocks"),
          integerOrDefault(row, "preference", 0),
          defaultValue(row, "source", "IMPORT"));
    }
    for (ImportRow row : file.rows().get("fixed_breaks")) {
      jdbc.update(
          "insert into fixed_break(journey_id,day_of_week,start_block,duration_blocks,reason) values (?,?,?,?,?)",
          journeys.get(cell(row, "journey_code")),
          integer(row, "day_of_week"),
          integer(row, "start_block"),
          integer(row, "duration_blocks"),
          blankToNull(cell(row, "reason")));
    }
    Map<String, Long> commonAreas = new HashMap<>();
    for (ImportRow row : file.rows().get("common_areas")) {
      Long id =
          insert(
              "insert into common_area_rule(code,course_id,journey_id,semester_number,name,active) values (?,?,?,?,?,?) returning id",
              cell(row, "code"),
              courses.get(cell(row, "course_code")),
              journeys.get(cell(row, "journey_code")),
              integer(row, "semester_number"),
              blankToNull(cell(row, "name")),
              boolOrDefault(row, "active", true));
      commonAreas.put(cell(row, "code"), id);
    }
    for (ImportRow row : file.rows().get("common_area_careers")) {
      jdbc.update(
          "insert into common_area_career(common_area_rule_id,career_id,curriculum_id) values (?,?,?)",
          commonAreas.get(cell(row, "common_area_code")),
          careers.get(cell(row, "career_code")),
          curricula.get(key(row, "career_code", "curriculum_code")));
    }
  }

  private Map<String, Long> insertCodeRows(List<ImportRow> rows, String table, String sql, RowArgs args) {
    Map<String, Long> ids = new HashMap<>();
    for (ImportRow row : rows) {
      ids.put(cell(row, "code"), insert(sql, args.args(row)));
    }
    return ids;
  }

  private Long insert(String sql, Object... args) {
    return jdbc.queryForObject(sql, Long.class, args);
  }

  private void validateRequiredCells(List<ImportRow> rows, List<String> columns, List<ImportError> errors) {
    for (ImportRow row : rows) {
      for (String column : columns) {
        if (cell(row, column).isBlank()) {
          errors.add(error(row.sheetName, row.rowNumber, column, "", "REQUIRED_VALUE", "El valor es obligatorio.", "Completar el valor."));
        }
      }
    }
  }

  private void validateInt(ImportRow row, String column, List<ImportError> errors) {
    String value = cell(row, column);
    if (value.isBlank()) {
      return;
    }
    try {
      Integer.parseInt(value);
    } catch (NumberFormatException ex) {
      errors.add(error(row.sheetName, row.rowNumber, column, value, "INVALID_VALUE", "Numero entero invalido.", "Usar un numero entero."));
    }
  }

  private void validateBool(ImportRow row, String column, List<ImportError> errors) {
    String value = cell(row, column);
    if (value.isBlank()) {
      return;
    }
    if (!Set.of("true", "false").contains(value.toLowerCase(Locale.ROOT))) {
      errors.add(error(row.sheetName, row.rowNumber, column, value, "INVALID_VALUE", "Booleano invalido.", "Usar true o false."));
    }
  }

  private void validateDate(ImportRow row, String column, List<ImportError> errors) {
    String value = cell(row, column);
    if (value.isBlank()) {
      return;
    }
    try {
      LocalDate.parse(value);
    } catch (RuntimeException ex) {
      errors.add(error(row.sheetName, row.rowNumber, column, value, "INVALID_VALUE", "Fecha invalida.", "Usar formato yyyy-MM-dd."));
    }
  }

  private void validateTime(ImportRow row, String column, List<ImportError> errors) {
    String value = cell(row, column);
    if (value.isBlank()) {
      return;
    }
    try {
      LocalTime.parse(value);
    } catch (RuntimeException ex) {
      errors.add(error(row.sheetName, row.rowNumber, column, value, "INVALID_VALUE", "Hora invalida.", "Usar formato HH:mm."));
    }
  }

  private void checkUnique(
      List<ImportRow> rows, String column, String table, String entity, List<ImportError> errors) {
    Set<String> seen = new HashSet<>();
    for (ImportRow row : rows) {
      String value = cell(row, column);
      if (value.isBlank()) {
        continue;
      }
      if (!seen.add(value)) {
        errors.add(error(row.sheetName, row.rowNumber, column, value, "DUPLICATE_CODE", "Codigo repetido en archivo.", "Dejar un codigo unico."));
      }
      Integer exists = jdbc.queryForObject("select count(*) from " + table + " where code = ?", Integer.class, value);
      if (exists != null && exists > 0) {
        errors.add(error(row.sheetName, row.rowNumber, column, value, "DUPLICATE_CODE", "Codigo ya existe.", "Usar otro codigo o eliminar el registro existente."));
      }
    }
  }

  private void require(Set<String> values, ImportRow row, String column, String message, List<ImportError> errors) {
    require(values, cell(row, column), row, column, message, errors);
  }

  private void require(
      Set<String> values, String value, ImportRow row, String column, String message, List<ImportError> errors) {
    if (!value.isBlank() && !values.contains(value)) {
      errors.add(error(row.sheetName, row.rowNumber, column, value, "RESOURCE_NOT_FOUND", message, "Crear el registro referenciado o corregir el codigo."));
    }
  }

  private Set<String> existingCodes(String table) {
    return new HashSet<>(jdbc.queryForList("select code from " + table, String.class));
  }

  private Codes codes(ImportFile file) {
    Codes codes = new Codes(existingCodes("career"), existingCodes("course"), existingCodes("teacher"), existingCodes("journey"), existingCodes("common_area_rule"));
    file.rows().get("careers").forEach(row -> codes.careers.add(cell(row, "code")));
    file.rows().get("courses").forEach(row -> codes.courses.add(cell(row, "code")));
    file.rows().get("teachers").forEach(row -> codes.teachers.add(cell(row, "code")));
    file.rows().get("journeys").forEach(row -> codes.journeys.add(cell(row, "code")));
    file.rows().get("common_areas").forEach(row -> codes.commonAreas.add(cell(row, "code")));
    file.rows().get("curricula").forEach(row -> codes.curricula.add(key(row, "career_code", "code")));
    return codes;
  }

  private long systemUserId() {
    Long id =
        client
            .sql("select id from app_user where email = :email")
            .param("email", "import@system.local")
            .query(Long.class)
            .optional()
            .orElse(null);
    if (id != null) {
      return id;
    }
    return insert(
        """
        insert into app_user(email,password_hash,full_name,role)
        values ('import@system.local','system','Import System','ADMIN'::user_role)
        returning id
        """);
  }

  private long createBatch(long userId, String filename) {
    return insert(
        "insert into import_batch(uploaded_by,filename,status) values (?,?,'UPLOADED'::import_batch_status) returning id",
        userId,
        filename);
  }

  private void saveErrors(long batchId, List<ImportError> errors) {
    for (ImportError error : errors) {
      jdbc.update(
          """
          insert into import_error(import_batch_id,sheet_name,row_number,column_name,raw_value,code,message,suggested_action)
          values (?,?,?,?,?,?,?,?)
          """,
          batchId,
          error.sheetName,
          error.rowNumber,
          error.columnName,
          error.rawValue,
          error.code,
          error.message,
          error.suggestedAction);
    }
  }

  private void finishBatch(long id, String status, Map<String, Integer> summary) {
    jdbc.update(
        """
        update import_batch
        set status = ?::import_batch_status,
            finished_at = ?,
            summary = ?::jsonb
        where id = ?
        """,
        status,
        Timestamp.from(java.time.Instant.now()),
        "{\"rowsRead\":%d,\"rowsValid\":%d,\"rowsInvalid\":%d}"
            .formatted(summary.get("rowsRead"), summary.get("rowsValid"), summary.get("rowsInvalid")),
        id);
  }

  private ImportErrorResponse errorResponse(ResultSet rs) throws java.sql.SQLException {
    return new ImportErrorResponse(
        rs.getLong("id"),
        rs.getString("sheet_name"),
        (Integer) rs.getObject("row_number"),
        rs.getString("column_name"),
        rs.getString("raw_value"),
        rs.getString("code"),
        rs.getString("message"),
        rs.getString("suggested_action"));
  }

  private Map<String, List<ImportRow>> emptyRows() {
    Map<String, List<ImportRow>> rows = new LinkedHashMap<>();
    REQUIRED_SHEETS.forEach(sheet -> rows.put(sheet, new ArrayList<>()));
    return rows;
  }

  private List<String> headers(Row row, DataFormatter formatter) {
    List<String> headers = new ArrayList<>();
    for (Cell cell : row) {
      headers.add(normalize(formatter.formatCellValue(cell)));
    }
    return headers;
  }

  private Map<String, String> values(Row row, List<String> headers, DataFormatter formatter) {
    Map<String, String> values = new LinkedHashMap<>();
    for (int i = 0; i < headers.size(); i++) {
      values.put(headers.get(i), formatter.formatCellValue(row.getCell(i)).trim());
    }
    return values;
  }

  private List<String> splitCsv(String line) {
    List<String> values = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    boolean quoted = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        quoted = !quoted;
      } else if (c == ',' && !quoted) {
        values.add(current.toString());
        current.setLength(0);
      } else {
        current.append(c);
      }
    }
    values.add(current.toString());
    return values;
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().replace('-', '_').toLowerCase(Locale.ROOT);
  }

  private static String cell(ImportRow row, String column) {
    return row.values.getOrDefault(column, "").trim();
  }

  private static int integer(ImportRow row, String column) {
    return Integer.parseInt(cell(row, column));
  }

  private static int integerOrDefault(ImportRow row, String column, int fallback) {
    String value = cell(row, column);
    return value.isBlank() ? fallback : Integer.parseInt(value);
  }

  private static boolean bool(ImportRow row, String column) {
    return Boolean.parseBoolean(cell(row, column));
  }

  private static boolean boolOrDefault(ImportRow row, String column, boolean fallback) {
    String value = cell(row, column);
    return value.isBlank() ? fallback : Boolean.parseBoolean(value);
  }

  private static Date dateOrNull(ImportRow row, String column) {
    String value = cell(row, column);
    return value.isBlank() ? null : Date.valueOf(LocalDate.parse(value));
  }

  private static LocalTime time(ImportRow row, String column) {
    return LocalTime.parse(cell(row, column));
  }

  private static String key(ImportRow row, String first, String second) {
    return cell(row, first) + "|" + cell(row, second);
  }

  private static String defaultValue(ImportRow row, String column, String fallback) {
    String value = cell(row, column);
    return value.isBlank() ? fallback : value;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static ImportError error(
      String sheet,
      Integer row,
      String column,
      String value,
      String code,
      String message,
      String action) {
    return new ImportError(sheet, row, column, value, code, message, action);
  }

  private record ImportFile(Map<String, List<ImportRow>> rows, Map<String, Set<String>> columns) {}

  private record ImportRow(String sheetName, int rowNumber, Map<String, String> values) {}

  private record ImportError(
      String sheetName,
      Integer rowNumber,
      String columnName,
      String rawValue,
      String code,
      String message,
      String suggestedAction) {}

  private interface RowArgs {
    Object[] args(ImportRow row);
  }

  private static final class Codes {
    final Set<String> careers;
    final Set<String> courses;
    final Set<String> teachers;
    final Set<String> journeys;
    final Set<String> commonAreas;
    final Set<String> curricula = new HashSet<>();

    Codes(
        Set<String> careers,
        Set<String> courses,
        Set<String> teachers,
        Set<String> journeys,
        Set<String> commonAreas) {
      this.careers = careers;
      this.courses = courses;
      this.teachers = teachers;
      this.journeys = journeys;
      this.commonAreas = commonAreas;
    }
  }
}
