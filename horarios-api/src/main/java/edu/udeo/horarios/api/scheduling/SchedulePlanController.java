package edu.udeo.horarios.api.scheduling;

import edu.udeo.horarios.api.catalog.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule-plans")
class SchedulePlanController {
  private final ScheduleGenerationService service;

  SchedulePlanController(ScheduleGenerationService service) {
    this.service = service;
  }

  @GetMapping
  PageResponse<SchedulePlanResponse> list(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "status", required = false) String status,
      @RequestParam(name = "scheduleType", required = false) String scheduleType,
      @RequestParam(name = "q", required = false) String q) {
    return service.listPlans(page, size, status, scheduleType, q);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  SchedulePlanResponse create(@RequestBody SchedulePlanRequest request) {
    return service.createPlan(request);
  }
}
