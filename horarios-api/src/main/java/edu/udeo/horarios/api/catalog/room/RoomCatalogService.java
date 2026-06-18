package edu.udeo.horarios.api.catalog.room;

import edu.udeo.horarios.api.catalog.common.CatalogService;
import edu.udeo.horarios.api.catalog.common.NotFoundException;
import edu.udeo.horarios.api.catalog.common.PageResponse;
import edu.udeo.horarios.api.catalog.common.PatchMapper;
import edu.udeo.horarios.api.catalog.common.RequestMapper;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
class RoomCatalogService implements CatalogService {
  private final RoomRepository rooms;
  private final RequestMapper mapper;
  private final PatchMapper patchMapper;

  RoomCatalogService(RoomRepository rooms, RequestMapper mapper, PatchMapper patchMapper) {
    this.rooms = rooms;
    this.mapper = mapper;
    this.patchMapper = patchMapper;
  }

  @Override
  public Set<String> resources() {
    return Set.of("rooms");
  }

  @Override
  public Object create(String resource, Object request) {
    RoomRequest dto = mapper.convert(request, RoomRequest.class);
    if (rooms.existsByCode(dto.code())) {
      throw new DataIntegrityViolationException("duplicate room code");
    }
    RoomEntity room = new RoomEntity();
    room.setCode(dto.code());
    room.setCapacity(dto.capacity());
    room.setType(dto.type());
    room.setFloor(dto.floor());
    room.setNumber(dto.number());
    room.setActive(dto.active());
    return rooms.save(room);
  }

  @Override
  public Object patch(String resource, Long id, Object request) {
    RoomEntity room = rooms.findById(id).orElseThrow(() -> new NotFoundException("room"));
    var patch = patchMapper.map(request, Set.of("code", "capacity", "type", "floor", "number", "active"));
    if (patch.containsKey("code")) {
      String code = patchMapper.string(patch, "code");
      if (!code.equals(room.getCode()) && rooms.existsByCode(code)) {
        throw new DataIntegrityViolationException("duplicate room code");
      }
      room.setCode(code);
    }
    if (patch.containsKey("capacity")) {
      room.setCapacity(patchMapper.integer(patch, "capacity"));
    }
    if (room.getCapacity() < 1) {
      throw patchMapper.invalid("capacity");
    }
    if (patch.containsKey("type")) {
      room.setType(patchMapper.enumValue(patch, "type", RoomType.class));
    }
    if (patch.containsKey("floor")) {
      room.setFloor(patchMapper.integer(patch, "floor"));
    }
    if (patch.containsKey("number")) {
      room.setNumber(patchMapper.integer(patch, "number"));
    }
    if (patch.containsKey("active")) {
      room.setActive(patchMapper.bool(patch, "active"));
    }
    return rooms.save(room);
  }

  @Override
  public PageResponse<?> list(String resource, Pageable pageable) {
    Page<RoomEntity> page = rooms.findAll(pageable);
    return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  record RoomRequest(
      @NotBlank String code,
      @Min(1) int capacity,
      @NotNull RoomType type,
      int floor,
      int number,
      boolean active) {}
}
