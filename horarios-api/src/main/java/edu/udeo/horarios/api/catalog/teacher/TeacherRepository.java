package edu.udeo.horarios.api.catalog.teacher;

import org.springframework.data.jpa.repository.JpaRepository;

interface TeacherRepository extends JpaRepository<TeacherEntity, Long> {
  boolean existsByCode(String code);
}
