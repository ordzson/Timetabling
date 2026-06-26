package edu.udeo.horarios.api.catalog.extra;

import edu.udeo.horarios.api.catalog.common.CatalogService;
import edu.udeo.horarios.api.catalog.common.FieldErrorDto;
import edu.udeo.horarios.api.catalog.common.PageResponse;
import edu.udeo.horarios.api.catalog.common.RequestValidationException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
class PendingCatalogService implements CatalogService {
  private final JdbcClient jdbc;
  private final Map<String, ResourceDef> resources;

  PendingCatalogService(JdbcClient jdbc) {
    this.jdbc = jdbc;
    this.resources = Map.ofEntries(
        def("curricula", "curriculum", "id", "code", List.of("code", "careerId", "year", "isActive", "validFrom", "validUntil")),
        def("curriculum-courses", "curriculum_course", "id", "id", List.of("curriculumId", "courseId", "semesterNumber")),
        def("cohorts", "cohort", "id", "id", List.of("careerId", "curriculumId", "semesterNumber", "section", "journeyId", "expectedStudents", "active")),
        def("teacher-courses", "teacher_course", "id", "id", List.of("teacherId", "courseId", "preference")),
        def("teacher-availability", "teacher_availability", "id", "id", List.of("teacherId", "journeyId", "dayOfWeek", "startBlock", "durationBlocks", "preference", "source")),
        def("teacher-career-journeys", "teacher_career_journey", "id", "id", List.of("teacherId", "careerId", "journeyId", "active")),
        def("fixed-breaks", "fixed_break", "id", "id", List.of("journeyId", "dayOfWeek", "startBlock", "durationBlocks", "reason")),
        def("common-areas", "common_area_rule", "id", "code", List.of("code", "courseId", "journeyId", "semesterNumber", "name", "active")),
        def("common-area-rules", "common_area_rule", "id", "code", List.of("code", "courseId", "journeyId", "semesterNumber", "name", "active")),
        def("common-area-careers", "common_area_career", "id", "id", List.of("commonAreaRuleId", "careerId", "curriculumId")),
        def("resources", "resource", "id", "code", List.of("code", "name")),
        def("room-resources", "room_resource", null, "roomId", List.of("roomId", "resourceId")),
        def("course-required-resources", "course_required_resource", null, "courseId", List.of("courseId", "resourceId")));
  }

  @Override
  public Set<String> resources() {
    return resources.keySet();
  }

  @Override
  public Object create(String resource, Object request) {
    ResourceDef def = def(resource);
    Map<String, Object> fields = requiredMap(request, def.fields());
    String sql = "insert into " + def.table() + " (" + columns(fields.keySet()) + ") values (" + params(fields.keySet()) + ")";
    if (def.idColumn() != null) {
      Long id = jdbc.sql(sql + " returning " + def.idColumn()).params(dbParams(fields)).query(Long.class).single();
      return findById(def, id, resource);
    }
    jdbc.sql(sql).params(dbParams(fields)).update();
    return fields;
  }

  @Override
  public Object patch(String resource, Long id, Object request) {
    ResourceDef def = def(resource);
    if (def.idColumn() == null) {
      throw invalid("id");
    }
    Map<String, Object> fields = patchMap(request, def.fields());
    if (fields.isEmpty()) {
      throw invalid("body");
    }
    String sql = "update " + def.table() + " set " + assignments(fields.keySet()) + " where " + def.idColumn() + " = :id";
    Map<String, Object> params = dbParams(fields);
    params.put("id", id);
    if (jdbc.sql(sql).params(params).update() == 0) {
      throw new edu.udeo.horarios.api.catalog.common.NotFoundException(resource);
    }
    return findById(def, id, resource);
  }

  @Override
  public PageResponse<?> list(String resource, Pageable pageable) {
    ResourceDef def = def(resource);
    int limit = pageable.getPageSize();
    int offset = (int) pageable.getOffset();
    String order = orderBy(def, pageable);
    List<Map<String, Object>> items =
        jdbc.sql("select " + selectClause(def) + " from " + def.table() + " order by " + order + " limit :limit offset :offset")
            .param("limit", limit)
            .param("offset", offset)
            .query()
            .listOfRows();
    long total = jdbc.sql("select count(*) from " + def.table()).query(Long.class).single();
    int pages = (int) Math.ceil((double) total / limit);
    return new PageResponse<>(items, pageable.getPageNumber(), limit, total, pages);
  }

  private ResourceDef def(String resource) {
    ResourceDef def = resources.get(resource);
    if (def == null) {
      throw new edu.udeo.horarios.api.catalog.common.NotFoundException(resource);
    }
    return def;
  }

  private Map<String, Object> findById(ResourceDef def, Long id, String resource) {
    List<Map<String, Object>> rows =
        jdbc.sql("select " + selectClause(def) + " from " + def.table() + " where " + def.idColumn() + " = :id")
            .param("id", id)
            .query()
            .listOfRows();
    if (rows.isEmpty()) {
      throw new edu.udeo.horarios.api.catalog.common.NotFoundException(resource);
    }
    return rows.getFirst();
  }

  private static Map.Entry<String, ResourceDef> def(String resource, String table, String idColumn, String defaultSort, List<String> fields) {
    return Map.entry(resource, new ResourceDef(table, idColumn, defaultSort, fields));
  }

  private Map<String, Object> requiredMap(Object request, List<String> allowed) {
    Map<String, Object> map = patchMap(request, allowed);
    List<FieldErrorDto> missing = allowed.stream()
        .filter(field -> !nullable(field))
        .filter(field -> !map.containsKey(field))
        .map(field -> new FieldErrorDto(field, "El campo es obligatorio."))
        .toList();
    if (!missing.isEmpty()) {
      throw new RequestValidationException(missing);
    }
    return map;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> patchMap(Object request, List<String> allowed) {
    if (!(request instanceof Map<?, ?> raw)) {
      throw invalid("body");
    }
    Map<String, Object> map = new LinkedHashMap<>();
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      if (!(entry.getKey() instanceof String field) || !allowed.contains(field)) {
        throw invalid(String.valueOf(entry.getKey()));
      }
      map.put(field, convert(field, entry.getValue()));
    }
    return map;
  }

  private Object convert(String field, Object value) {
    if (value == null) {
      return null;
    }
    if (field.endsWith("Id") || field.equals("year") || field.equals("semesterNumber") || field.equals("expectedStudents")
        || field.equals("dayOfWeek") || field.equals("startBlock") || field.equals("durationBlocks") || field.equals("preference")) {
      if (value instanceof Number number) {
        return number.longValue();
      }
      throw invalid(field);
    }
    if (field.equals("isActive") || field.equals("active")) {
      if (value instanceof Boolean bool) {
        return bool;
      }
      throw invalid(field);
    }
    if (field.equals("validFrom") || field.equals("validUntil")) {
      try {
        return value instanceof String text && !text.isBlank() ? LocalDate.parse(text) : null;
      } catch (RuntimeException ex) {
        throw invalid(field);
      }
    }
    if (field.equals("startTime") || field.equals("endTime")) {
      try {
        return value instanceof String text && !text.isBlank() ? LocalTime.parse(text) : null;
      } catch (RuntimeException ex) {
        throw invalid(field);
      }
    }
    if (value instanceof String text) {
      if (text.isBlank() && !nullable(field)) {
        throw invalid(field);
      }
      return text.isBlank() ? null : text;
    }
    throw invalid(field);
  }

  private static boolean nullable(String field) {
    return Set.of("validFrom", "validUntil", "journeyId", "reason", "name").contains(field);
  }

  private static Map<String, Object> dbParams(Map<String, Object> fields) {
    Map<String, Object> params = new LinkedHashMap<>();
    fields.forEach((field, value) -> params.put(toSnake(field), value));
    return params;
  }

  private static String columns(Set<String> fields) {
    return String.join(", ", fields.stream().map(PendingCatalogService::toSnake).toList());
  }

  private static String params(Set<String> fields) {
    return ":" + String.join(", :", fields.stream().map(PendingCatalogService::toSnake).toList());
  }

  private static String assignments(Set<String> fields) {
    List<String> parts = new ArrayList<>();
    for (String field : fields) {
      String column = toSnake(field);
      parts.add(column + " = :" + column);
    }
    return String.join(", ", parts);
  }

  private static String selectClause(ResourceDef def) {
    List<String> columns = new ArrayList<>();
    if (def.idColumn() != null) {
      columns.add(def.idColumn() + " as \"id\"");
    }
    for (String field : def.fields()) {
      columns.add(toSnake(field) + " as \"" + field + "\"");
    }
    return String.join(", ", columns);
  }

  private static String orderBy(ResourceDef def, Pageable pageable) {
    String fallback = def.defaultSort();
    if (!pageable.getSort().isSorted()) {
      return fallback;
    }
    var order = pageable.getSort().iterator().next();
    String field = order.getProperty();
    if (!field.equals(def.idColumn()) && !def.fields().contains(field)) {
      field = fallback;
    }
    return toSnake(field) + (order.isDescending() ? " desc" : " asc");
  }

  private static String toSnake(String text) {
    return text.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
  }

  private static RequestValidationException invalid(String field) {
    return new RequestValidationException(List.of(new FieldErrorDto(field, "Campo invalido.")));
  }

  record ResourceDef(String table, String idColumn, String defaultSort, List<String> fields) {}
}
