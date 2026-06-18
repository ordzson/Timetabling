package edu.udeo.horarios.api.catalog.time;

import org.springframework.data.jpa.repository.JpaRepository;

interface JourneyRepository extends JpaRepository<JourneyEntity, Long> {
  boolean existsByCode(String code);
}
