package edu.udeo.horarios.api.catalog.academic;

import org.springframework.data.jpa.repository.JpaRepository;

interface CareerRepository extends JpaRepository<CareerEntity, Long> {
  boolean existsByCode(String code);
}
