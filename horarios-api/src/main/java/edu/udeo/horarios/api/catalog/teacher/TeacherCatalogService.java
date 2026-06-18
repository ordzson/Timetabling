package edu.udeo.horarios.api.catalog.teacher;

import edu.udeo.horarios.api.catalog.common.CatalogService;
import edu.udeo.horarios.api.catalog.common.NotFoundException;
import edu.udeo.horarios.api.catalog.common.PageResponse;
import edu.udeo.horarios.api.catalog.common.PatchMapper;
import edu.udeo.horarios.api.catalog.common.RequestMapper;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
class TeacherCatalogService implements CatalogService {
  private final TeacherRepository teachers;
  private final RequestMapper mapper;
  private final PatchMapper patchMapper;

  TeacherCatalogService(TeacherRepository teachers, RequestMapper mapper, PatchMapper patchMapper) {
    this.teachers = teachers;
    this.mapper = mapper;
    this.patchMapper = patchMapper;
  }

  @Override
  public Set<String> resources() {
    return Set.of("teachers");
  }

  @Override
  public Object create(String resource, Object request) {
    TeacherRequest dto = mapper.convert(request, TeacherRequest.class);
    if (teachers.existsByCode(dto.code())) {
      throw new DataIntegrityViolationException("duplicate teacher code");
    }
    TeacherEntity teacher = new TeacherEntity();
    teacher.setCode(dto.code());
    teacher.setFullName(dto.fullName());
    teacher.setPriority(dto.priority());
    teacher.setMinCourses(dto.minCourses());
    teacher.setMaxCourses(dto.maxCourses());
    teacher.setActive(dto.active());
    return teachers.save(teacher);
  }

  @Override
  public Object patch(String resource, Long id, Object request) {
    TeacherEntity teacher = teachers.findById(id).orElseThrow(() -> new NotFoundException("teacher"));
    var patch =
        patchMapper.map(
            request, Set.of("code", "fullName", "priority", "minCourses", "maxCourses", "active"));
    if (patch.containsKey("code")) {
      String code = patchMapper.string(patch, "code");
      if (!code.equals(teacher.getCode()) && teachers.existsByCode(code)) {
        throw new DataIntegrityViolationException("duplicate teacher code");
      }
      teacher.setCode(code);
    }
    if (patch.containsKey("fullName")) {
      teacher.setFullName(patchMapper.string(patch, "fullName"));
    }
    if (patch.containsKey("priority")) {
      teacher.setPriority(patchMapper.integer(patch, "priority"));
    }
    if (patch.containsKey("minCourses")) {
      teacher.setMinCourses(patchMapper.integer(patch, "minCourses"));
    }
    if (patch.containsKey("maxCourses")) {
      teacher.setMaxCourses(patchMapper.integer(patch, "maxCourses"));
    }
    if (teacher.getMinCourses() < 0 || teacher.getMaxCourses() < teacher.getMinCourses()) {
      throw patchMapper.invalid("maxCourses");
    }
    if (patch.containsKey("active")) {
      teacher.setActive(patchMapper.bool(patch, "active"));
    }
    return teachers.save(teacher);
  }

  @Override
  public PageResponse<?> list(String resource, Pageable pageable) {
    Page<TeacherEntity> page = teachers.findAll(pageable);
    return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  record TeacherRequest(
      @NotBlank String code,
      @NotBlank String fullName,
      int priority,
      @Min(0) int minCourses,
      @Min(0) int maxCourses,
      boolean active) {
    @AssertTrue(message = "maxCourses debe ser mayor o igual que minCourses.")
    boolean isLoadRangeValid() {
      return maxCourses >= minCourses;
    }
  }
}
