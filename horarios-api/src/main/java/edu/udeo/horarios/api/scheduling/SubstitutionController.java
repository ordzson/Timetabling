package edu.udeo.horarios.api.scheduling;

import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/substitutions")
class SubstitutionController {
  private final SubstitutionService service;

  SubstitutionController(SubstitutionService service) {
    this.service = service;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  SubstitutionResponse create(@RequestBody SubstitutionRequest request) {
    return service.create(request);
  }

  @GetMapping
  SubstitutionListResponse list(
      @RequestParam(name = "planId", required = false) Long planId,
      @RequestParam(name = "teacherId", required = false) Long teacherId,
      @RequestParam(name = "activeAt", required = false) Instant activeAt) {
    return service.list(planId, teacherId, activeAt);
  }
}
