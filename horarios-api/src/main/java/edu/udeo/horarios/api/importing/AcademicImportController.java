package edu.udeo.horarios.api.importing;

import edu.udeo.horarios.api.catalog.common.PageResponse;
import java.io.IOException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
class AcademicImportController {
  private final AcademicImportService imports;

  AcademicImportController(AcademicImportService imports) {
    this.imports = imports;
  }

  @PostMapping("/academic-data")
  ImportResponse importAcademicData(
      @RequestParam("file") MultipartFile file,
      @RequestParam(name = "mode", defaultValue = "IMPORT") ImportMode mode)
      throws IOException {
    return imports.importAcademicData(file, mode);
  }

  @GetMapping("/{id}/errors")
  PageResponse<ImportErrorResponse> errors(
      @PathVariable("id") long id,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "sheetName", required = false) String sheetName) {
    return imports.errors(id, page, size, sheetName);
  }
}
