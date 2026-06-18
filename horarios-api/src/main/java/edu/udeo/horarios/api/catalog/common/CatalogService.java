package edu.udeo.horarios.api.catalog.common;

import java.util.Set;
import org.springframework.data.domain.Pageable;

public interface CatalogService {
  Set<String> resources();

  Object create(String resource, Object request);

  Object patch(String resource, Long id, Object request);

  PageResponse<?> list(String resource, Pageable pageable);
}
