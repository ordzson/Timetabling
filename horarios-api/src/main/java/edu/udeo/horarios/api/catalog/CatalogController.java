package edu.udeo.horarios.api.catalog;

import edu.udeo.horarios.api.catalog.common.CatalogService;
import edu.udeo.horarios.api.catalog.common.BadRequestException;
import edu.udeo.horarios.api.catalog.common.NotFoundException;
import edu.udeo.horarios.api.catalog.common.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/catalog/{resource}")
class CatalogController {
  private final List<CatalogService> services;

  CatalogController(List<CatalogService> services) {
    this.services = services;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  Object create(@PathVariable("resource") String resource, @Valid @RequestBody Object request) {
    return service(resource).create(resource, request);
  }

  @PatchMapping("/{id}")
  Object patch(
      @PathVariable("resource") String resource,
      @PathVariable("id") Long id,
      @Valid @RequestBody Object request) {
    return service(resource).patch(resource, id, request);
  }

  @GetMapping
  PageResponse<?> list(
      @PathVariable("resource") String resource,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size,
      @RequestParam(name = "sort", defaultValue = "code,asc") String sort) {
    return service(resource).list(resource, pageable(page, size, sort));
  }

  private CatalogService service(String resource) {
    return services.stream()
        .filter(service -> service.resources().contains(resource))
        .findFirst()
        .orElseThrow(() -> new NotFoundException(resource));
  }

  private Pageable pageable(int page, int size, String sort) {
    if (page < 0 || size < 1 || size > 100) {
      throw new BadRequestException("Paginacion invalida.");
    }
    String[] parts = sort.split(",", 2);
    Sort.Direction direction = parts.length == 2 ? Sort.Direction.fromString(parts[1]) : Sort.Direction.ASC;
    return PageRequest.of(page, size, Sort.by(direction, parts[0]));
  }
}
