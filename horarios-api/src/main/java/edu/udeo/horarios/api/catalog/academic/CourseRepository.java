package edu.udeo.horarios.api.catalog.academic;

import org.springframework.data.jpa.repository.JpaRepository;

interface CourseRepository extends JpaRepository<CourseEntity, Long> {
  boolean existsByCode(String code);
}
