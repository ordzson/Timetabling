package edu.udeo.horarios.api.teacher;

import edu.udeo.horarios.api.catalog.common.FieldErrorDto;
import edu.udeo.horarios.api.catalog.common.RequestValidationException;
import edu.udeo.horarios.api.security.AppUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/teacher/availability")
class TeacherAvailabilityController {
  private final JdbcClient jdbc;

  TeacherAvailabilityController(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @GetMapping
  AvailabilityResponse get(Authentication authentication) {
    long teacherId = teacherId(authentication);
    return new AvailabilityResponse(teacherId, rows(teacherId));
  }

  @PutMapping
  @Transactional
  AvailabilityResponse put(Authentication authentication, @Valid @RequestBody AvailabilityRequest request) {
    long teacherId = teacherId(authentication);
    jdbc.sql("delete from teacher_availability where teacher_id = :teacherId")
        .param("teacherId", teacherId)
        .update();
    for (AvailabilityItem item : request.items()) {
      jdbc.sql("""
          insert into teacher_availability
            (teacher_id, journey_id, day_of_week, start_block, duration_blocks, preference, source)
          values
            (:teacherId, :journeyId, :dayOfWeek, :startBlock, :durationBlocks, :preference, 'PORTAL')
          """)
          .param("teacherId", teacherId)
          .param("journeyId", item.journeyId())
          .param("dayOfWeek", item.dayOfWeek())
          .param("startBlock", item.startBlock())
          .param("durationBlocks", item.durationBlocks())
          .param("preference", item.preference())
          .update();
    }
    return new AvailabilityResponse(teacherId, rows(teacherId));
  }

  private List<Map<String, Object>> rows(long teacherId) {
    return jdbc.sql("""
        select journey_id as "journeyId",
               day_of_week as "dayOfWeek",
               start_block as "startBlock",
               duration_blocks as "durationBlocks",
               preference,
               source
        from teacher_availability
        where teacher_id = :teacherId
        order by day_of_week, start_block
        """)
        .param("teacherId", teacherId)
        .query()
        .listOfRows();
  }

  private long teacherId(Authentication authentication) {
    if (authentication != null
        && authentication.getPrincipal() instanceof AppUser user
        && user.teacherId() != null) {
      return user.teacherId();
    }
    throw new RequestValidationException(List.of(new FieldErrorDto("teacherId", "Usuario docente invalido.")));
  }

  record AvailabilityResponse(long teacherId, List<Map<String, Object>> items) {}

  record AvailabilityRequest(@NotNull List<@Valid AvailabilityItem> items) {}

  record AvailabilityItem(
      Long journeyId,
      @Min(1) @Max(7) int dayOfWeek,
      @Min(0) int startBlock,
      @Min(1) int durationBlocks,
      int preference) {}
}
