package edu.udeo.horarios.api.scheduling;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schedule-plans/{planId}")
class ScheduleGenerationController {
  private final ScheduleGenerationService service;
  private final ManualEditService manualEditService;

  ScheduleGenerationController(ScheduleGenerationService service, ManualEditService manualEditService) {
    this.service = service;
    this.manualEditService = manualEditService;
  }

  @PostMapping("/validate")
  ValidationResponse validate(@PathVariable("planId") long planId) {
    return service.validate(planId);
  }

  @PostMapping("/generate")
  GenerationResponse generate(
      @PathVariable("planId") long planId, @RequestBody(required = false) GenerationRequest request) {
    return service.generate(planId, request == null ? new GenerationRequest(null, null, null, null, Map.of()) : request);
  }

  @GetMapping("/result")
  ResultResponse result(
      @PathVariable("planId") long planId, @RequestParam(name = "runId", required = false) Long runId) {
    return service.result(planId, runId);
  }

  @GetMapping("/violations")
  ViolationsResponse violations(
      @PathVariable("planId") long planId,
      @RequestParam(name = "runId", required = false) Long runId,
      @RequestParam(name = "severity", required = false) String severity) {
    return service.violations(planId, runId, severity);
  }

  @PostMapping("/manual-edits")
  ManualEditResponse manualEdit(
      @PathVariable("planId") long planId, @RequestBody ManualEditRequest request) {
    return manualEditService.apply(planId, request);
  }
}
