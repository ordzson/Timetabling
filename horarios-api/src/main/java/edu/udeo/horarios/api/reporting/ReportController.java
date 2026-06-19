package edu.udeo.horarios.api.reporting;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
class ReportController {
  private static final MediaType XLSX =
      MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

  private final ReportService service;

  ReportController(ReportService service) {
    this.service = service;
  }

  @GetMapping(value = "/schedule-plans/{planId}.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  ResponseEntity<byte[]> pdf(
      @PathVariable("planId") long planId,
      @RequestParam(name = "runId", required = false) Long runId,
      @RequestParam(name = "view", defaultValue = "cohort") String view) {
    return file(service.pdf(planId, runId, view), MediaType.APPLICATION_PDF, "schedule-plan-" + planId + ".pdf");
  }

  @GetMapping(value = "/schedule-plans/{planId}.xlsx")
  ResponseEntity<byte[]> xlsx(
      @PathVariable("planId") long planId, @RequestParam(name = "runId", required = false) Long runId) {
    return file(service.xlsx(planId, runId), XLSX, "schedule-plan-" + planId + ".xlsx");
  }

  private ResponseEntity<byte[]> file(byte[] body, MediaType type, String filename) {
    return ResponseEntity.ok()
        .contentType(type)
        .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
        .body(body);
  }
}
