package edu.udeo.horarios.api.reporting;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class ReportService {
  private final JdbcTemplate jdbc;

  ReportService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  byte[] pdf(long planId, Long requestedRunId, String view) {
    ReportData data = data(planId, requestedRunId);
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    Document document = new Document(PageSize.LETTER.rotate());
    try {
      PdfWriter.getInstance(document, out);
      document.open();
      title(document, data);
      switch (view == null ? "cohort" : view) {
        case "teacher" -> pdfTable(document, "Por docente", byTeacher(data.assignments()));
        case "room" -> pdfTable(document, "Por aula", byRoom(data.assignments()));
        case "conflicts" -> pdfConflicts(document, data);
        default -> pdfTable(document, "Por cohorte", byCohort(data.assignments()));
      }
      document.close();
      return out.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("pdf report failed", ex);
    }
  }

  byte[] xlsx(long planId, Long requestedRunId) {
    ReportData data = data(planId, requestedRunId);
    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      CellStyle header = workbook.createCellStyle();
      org.apache.poi.ss.usermodel.Font font = workbook.createFont();
      font.setBold(true);
      header.setFont(font);
      sheet(workbook, "cohort", header, byCohort(data.assignments()));
      sheet(workbook, "teacher", header, byTeacher(data.assignments()));
      sheet(workbook, "room", header, byRoom(data.assignments()));
      conflicts(workbook, header, data);
      metadata(workbook, header, data.run());
      workbook.write(out);
      return out.toByteArray();
    } catch (Exception ex) {
      throw new IllegalStateException("xlsx report failed", ex);
    }
  }

  private ReportData data(long planId, Long requestedRunId) {
    long runId = requestedRunId == null ? latestRun(planId) : requestedRunId;
    RunMeta run =
        jdbc.query(
                """
                select p.id plan_id, p.code plan_code, p.name plan_name,
                       r.id run_id, r.run_number, r.solver_mode, r.seed, r.engine_version,
                       r.status::text, r.started_at, r.finished_at, r.config::text, r.score_breakdown::text
                from schedule_run r
                join schedule_plan p on p.id = r.plan_id
                where p.id = ? and r.id = ?
                """,
                (rs, rowNum) -> run(rs),
                planId,
                runId)
            .stream()
            .findFirst()
            .orElseThrow(() -> notFound());
    return new ReportData(run, assignments(planId, runId), violations(runId));
  }

  private List<AssignmentRow> assignments(long planId, long runId) {
    return jdbc.query(
        """
        select a.status::text, c.code course_code, c.name course_name,
               coalesce(t.full_name, '') teacher_name, coalesce(r.code, '') room_code,
               coalesce(string_agg(distinct ca.code || ' ' || co.semester_number || co.section, ', ' order by ca.code || ' ' || co.semester_number || co.section), '') cohorts,
               coalesce(tb.day_of_week, 0) day_of_week, coalesce(tb.block_index, 0) block_index,
               coalesce(a.duration_blocks, 0) duration_blocks, coalesce(a.unassigned_reason, '') unassigned_reason
        from schedule_assignment a
        join schedule_session s on s.id = a.session_id
        join course c on c.id = s.course_id
        left join teacher t on t.id = a.teacher_id
        left join room r on r.id = a.room_id
        left join time_block tb on tb.id = a.start_time_block_id
        left join schedule_session_cohort sc on sc.session_id = s.id
        left join cohort co on co.id = sc.cohort_id
        left join career ca on ca.id = co.career_id
        where a.plan_id = ? and a.run_id = ?
        group by a.id, c.code, c.name, t.full_name, r.code, tb.day_of_week, tb.block_index
        order by cohorts, day_of_week, block_index, course_code
        """,
        (rs, rowNum) ->
            new AssignmentRow(
                rs.getString("status"),
                rs.getString("course_code"),
                rs.getString("course_name"),
                rs.getString("teacher_name"),
                rs.getString("room_code"),
                rs.getString("cohorts"),
                rs.getInt("day_of_week"),
                rs.getInt("block_index"),
                rs.getInt("duration_blocks"),
                rs.getString("unassigned_reason")),
        planId,
        runId);
  }

  private List<ViolationRow> violations(long runId) {
    return jdbc.query(
        """
        select severity::text, code, message, affected_entities::text, cost
        from schedule_violation
        where run_id = ?
        order by id
        """,
        (rs, rowNum) ->
            new ViolationRow(
                rs.getString("severity"),
                rs.getString("code"),
                rs.getString("message"),
                rs.getString("affected_entities"),
                rs.getString("cost")),
        runId);
  }

  private void title(Document document, ReportData data) throws Exception {
    Font h1 = new Font(Font.HELVETICA, 16, Font.BOLD);
    document.add(new Paragraph("Horario " + data.run().planCode() + " - corrida " + data.run().runNumber(), h1));
    document.add(new Paragraph("seed: " + data.run().seed() + " | engineVersion: " + data.run().engineVersion()));
    document.add(new Paragraph("pesos/config: " + data.run().config()));
    document.add(new Paragraph("tiempos: " + data.run().startedAt() + " - " + data.run().finishedAt()));
    document.add(new Paragraph(" "));
  }

  private void pdfTable(Document document, String title, List<String[]> rows) throws Exception {
    document.add(new Paragraph(title, new Font(Font.HELVETICA, 13, Font.BOLD)));
    PdfPTable table = new PdfPTable(new float[] {2, 2, 3, 3, 2, 1, 1});
    table.setWidthPercentage(100);
    for (String header : new String[] {"Grupo", "Curso", "Nombre", "Docente", "Aula", "Dia", "Bloque"}) {
      table.addCell(cell(header, true));
    }
    for (String[] row : rows) {
      for (String value : row) {
        table.addCell(cell(value, false));
      }
    }
    document.add(table);
  }

  private void pdfConflicts(Document document, ReportData data) throws Exception {
    document.add(new Paragraph("Conflictos / no asignables", new Font(Font.HELVETICA, 13, Font.BOLD)));
    PdfPTable table = new PdfPTable(new float[] {2, 2, 5, 2});
    table.setWidthPercentage(100);
    for (String header : new String[] {"Severidad", "Codigo", "Mensaje", "Costo"}) {
      table.addCell(cell(header, true));
    }
    for (ViolationRow violation : data.violations()) {
      table.addCell(cell(violation.severity(), false));
      table.addCell(cell(violation.code(), false));
      table.addCell(cell(violation.message(), false));
      table.addCell(cell(violation.cost(), false));
    }
    for (AssignmentRow row : data.assignments().stream().filter(AssignmentRow::unassigned).toList()) {
      table.addCell(cell("ERROR", false));
      table.addCell(cell(row.unassignedReason(), false));
      table.addCell(cell("No asignable: " + row.courseCode(), false));
      table.addCell(cell("", false));
    }
    document.add(table);
  }

  private PdfPCell cell(String value, boolean header) {
    PdfPCell cell = new PdfPCell(new Phrase(value == null ? "" : value, new Font(Font.HELVETICA, 9, header ? Font.BOLD : Font.NORMAL)));
    cell.setHorizontalAlignment(header ? Element.ALIGN_CENTER : Element.ALIGN_LEFT);
    return cell;
  }

  private void sheet(Workbook workbook, String name, CellStyle header, List<String[]> rows) {
    Sheet sheet = workbook.createSheet(name);
    header(sheet, header, "grupo", "curso", "nombre", "docente", "aula", "dia", "bloque");
    int rowNum = 1;
    for (String[] values : rows) {
      Row row = sheet.createRow(rowNum++);
      for (int i = 0; i < values.length; i++) {
        row.createCell(i).setCellValue(values[i]);
      }
    }
    autosize(sheet, 7);
  }

  private void conflicts(Workbook workbook, CellStyle header, ReportData data) {
    Sheet sheet = workbook.createSheet("conflicts");
    header(sheet, header, "severity", "code", "message", "affected_entities", "cost");
    int rowNum = 1;
    for (ViolationRow violation : data.violations()) {
      Row row = sheet.createRow(rowNum++);
      row.createCell(0).setCellValue(violation.severity());
      row.createCell(1).setCellValue(violation.code());
      row.createCell(2).setCellValue(violation.message());
      row.createCell(3).setCellValue(violation.affectedEntities());
      row.createCell(4).setCellValue(violation.cost());
    }
    for (AssignmentRow assignment : data.assignments().stream().filter(AssignmentRow::unassigned).toList()) {
      Row row = sheet.createRow(rowNum++);
      row.createCell(0).setCellValue("ERROR");
      row.createCell(1).setCellValue(assignment.unassignedReason());
      row.createCell(2).setCellValue("No asignable: " + assignment.courseCode());
      row.createCell(3).setCellValue("");
      row.createCell(4).setCellValue("");
    }
    autosize(sheet, 5);
  }

  private void metadata(Workbook workbook, CellStyle header, RunMeta run) {
    Sheet sheet = workbook.createSheet("metadata");
    header(sheet, header, "key", "value");
    String[][] rows = {
      {"planId", String.valueOf(run.planId())},
      {"planCode", run.planCode()},
      {"runId", String.valueOf(run.runId())},
      {"runNumber", String.valueOf(run.runNumber())},
      {"status", run.status()},
      {"seed", String.valueOf(run.seed())},
      {"engineVersion", run.engineVersion()},
      {"weights/config", run.config()},
      {"score", run.scoreBreakdown()},
      {"startedAt", String.valueOf(run.startedAt())},
      {"finishedAt", String.valueOf(run.finishedAt())}
    };
    for (int i = 0; i < rows.length; i++) {
      Row row = sheet.createRow(i + 1);
      row.createCell(0).setCellValue(rows[i][0]);
      row.createCell(1).setCellValue(rows[i][1]);
    }
    autosize(sheet, 2);
  }

  private List<String[]> byCohort(List<AssignmentRow> rows) {
    return rows.stream().filter(row -> !row.unassigned()).map(row -> row.values(row.cohorts())).toList();
  }

  private List<String[]> byTeacher(List<AssignmentRow> rows) {
    return rows.stream().filter(row -> !row.unassigned()).map(row -> row.values(row.teacherName())).toList();
  }

  private List<String[]> byRoom(List<AssignmentRow> rows) {
    return rows.stream().filter(row -> !row.unassigned()).map(row -> row.values(row.roomCode())).toList();
  }

  private void header(Sheet sheet, CellStyle style, String... names) {
    Row row = sheet.createRow(0);
    for (int i = 0; i < names.length; i++) {
      row.createCell(i).setCellValue(names[i]);
      row.getCell(i).setCellStyle(style);
    }
  }

  private void autosize(Sheet sheet, int columns) {
    for (int i = 0; i < columns; i++) {
      sheet.autoSizeColumn(i);
    }
  }

  private long latestRun(long planId) {
    List<Long> ids =
        jdbc.queryForList(
            """
            select id
            from schedule_run
            where plan_id = ? and status in ('COMPLETED'::schedule_run_status, 'COMPLETED_WITH_CONFLICTS'::schedule_run_status)
            order by finished_at desc nulls last, id desc
            limit 1
            """,
            Long.class,
            planId);
    if (ids.isEmpty()) {
      throw notFound();
    }
    return ids.getFirst();
  }

  private ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "El recurso no existe.");
  }

  private RunMeta run(ResultSet rs) throws SQLException {
    return new RunMeta(
        rs.getLong("plan_id"),
        rs.getString("plan_code"),
        rs.getString("plan_name"),
        rs.getLong("run_id"),
        rs.getInt("run_number"),
        rs.getString("solver_mode"),
        rs.getLong("seed"),
        rs.getString("engine_version"),
        rs.getString("status"),
        instant(rs, "started_at"),
        instant(rs, "finished_at"),
        rs.getString("config"),
        rs.getString("score_breakdown"));
  }

  private Instant instant(ResultSet rs, String column) throws SQLException {
    Timestamp value = rs.getTimestamp(column);
    return value == null ? null : value.toInstant();
  }

  private record ReportData(RunMeta run, List<AssignmentRow> assignments, List<ViolationRow> violations) {
  }

  private record RunMeta(
      long planId,
      String planCode,
      String planName,
      long runId,
      int runNumber,
      String solverMode,
      long seed,
      String engineVersion,
      String status,
      Instant startedAt,
      Instant finishedAt,
      String config,
      String scoreBreakdown) {
  }

  private record AssignmentRow(
      String status,
      String courseCode,
      String courseName,
      String teacherName,
      String roomCode,
      String cohorts,
      int dayOfWeek,
      int blockIndex,
      int durationBlocks,
      String unassignedReason) {
    boolean unassigned() {
      return "UNASSIGNED".equals(status);
    }

    String[] values(String group) {
      return new String[] {group, courseCode, courseName, teacherName, roomCode, String.valueOf(dayOfWeek), String.valueOf(blockIndex)};
    }
  }

  private record ViolationRow(String severity, String code, String message, String affectedEntities, String cost) {
  }
}
