package edu.udeo.horarios.api.catalog.room;

import org.springframework.data.jpa.repository.JpaRepository;

interface RoomRepository extends JpaRepository<RoomEntity, Long> {
  boolean existsByCode(String code);
}
