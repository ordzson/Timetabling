package edu.udeo.horarios.api.catalog.academic;

import edu.udeo.horarios.api.catalog.common.CatalogService;
import edu.udeo.horarios.api.catalog.common.NotFoundException;
import edu.udeo.horarios.api.catalog.common.PageResponse;
import edu.udeo.horarios.api.catalog.common.PatchMapper;
import edu.udeo.horarios.api.catalog.common.RequestMapper;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
class AcademicCatalogService implements CatalogService {
  private final CareerRepository careers;
  private final CourseRepository courses;
  private final RequestMapper mapper;
  private final PatchMapper patchMapper;

  AcademicCatalogService(
      CareerRepository careers, CourseRepository courses, RequestMapper mapper, PatchMapper patchMapper) {
    this.careers = careers;
    this.courses = courses;
    this.mapper = mapper;
    this.patchMapper = patchMapper;
  }

  @Override
  public Set<String> resources() {
    return Set.of("careers", "courses");
  }

  @Override
  public Object create(String resource, Object request) {
    return switch (resource) {
      case "careers" -> createCareer(request);
      case "courses" -> createCourse(request);
      default -> throw new IllegalArgumentException(resource);
    };
  }

  @Override
  public Object patch(String resource, Long id, Object request) {
    return switch (resource) {
      case "careers" -> patchCareer(id, request);
      case "courses" -> patchCourse(id, request);
      default -> throw new IllegalArgumentException(resource);
    };
  }

  @Override
  public PageResponse<?> list(String resource, Pageable pageable) {
    return switch (resource) {
      case "careers" -> page(careers.findAll(pageable));
      case "courses" -> page(courses.findAll(pageable));
      default -> throw new IllegalArgumentException(resource);
    };
  }

  private CareerEntity createCareer(Object request) {
    CareerRequest dto = mapper.convert(request, CareerRequest.class);
    if (careers.existsByCode(dto.code())) {
      throw new DataIntegrityViolationException("duplicate career code");
    }
    CareerEntity career = new CareerEntity();
    career.setCode(dto.code());
    career.setName(dto.name());
    career.setActive(dto.active());
    return careers.save(career);
  }

  private CourseEntity createCourse(Object request) {
    CourseRequest dto = mapper.convert(request, CourseRequest.class);
    if (courses.existsByCode(dto.code())) {
      throw new DataIntegrityViolationException("duplicate course code");
    }
    CourseEntity course = new CourseEntity();
    course.setCode(dto.code());
    course.setName(dto.name());
    course.setRequiresLab(dto.requiresLab());
    course.setWeeklyBlocksMin(dto.weeklyBlocksMin());
    course.setWeeklyBlocksMax(dto.weeklyBlocksMax());
    course.setPreferences(dto.preferences());
    return courses.save(course);
  }

  private CareerEntity patchCareer(Long id, Object request) {
    CareerEntity career = careers.findById(id).orElseThrow(() -> new NotFoundException("career"));
    Map<String, Object> patch = patchMapper.map(request, Set.of("code", "name", "active"));
    if (patch.containsKey("code")) {
      String code = patchMapper.string(patch, "code");
      if (!code.equals(career.getCode()) && careers.existsByCode(code)) {
        throw new DataIntegrityViolationException("duplicate career code");
      }
      career.setCode(code);
    }
    if (patch.containsKey("name")) {
      career.setName(patchMapper.string(patch, "name"));
    }
    if (patch.containsKey("active")) {
      career.setActive(patchMapper.bool(patch, "active"));
    }
    return careers.save(career);
  }

  private CourseEntity patchCourse(Long id, Object request) {
    CourseEntity course = courses.findById(id).orElseThrow(() -> new NotFoundException("course"));
    Map<String, Object> patch =
        patchMapper.map(
            request,
            Set.of("code", "name", "requiresLab", "weeklyBlocksMin", "weeklyBlocksMax", "preferences"));
    if (patch.containsKey("code")) {
      String code = patchMapper.string(patch, "code");
      if (!code.equals(course.getCode()) && courses.existsByCode(code)) {
        throw new DataIntegrityViolationException("duplicate course code");
      }
      course.setCode(code);
    }
    if (patch.containsKey("name")) {
      course.setName(patchMapper.string(patch, "name"));
    }
    if (patch.containsKey("requiresLab")) {
      course.setRequiresLab(patchMapper.bool(patch, "requiresLab"));
    }
    if (patch.containsKey("weeklyBlocksMin")) {
      course.setWeeklyBlocksMin(patchMapper.integer(patch, "weeklyBlocksMin"));
    }
    if (patch.containsKey("weeklyBlocksMax")) {
      course.setWeeklyBlocksMax(patchMapper.integer(patch, "weeklyBlocksMax"));
    }
    if (course.getWeeklyBlocksMin() < 1 || course.getWeeklyBlocksMax() < course.getWeeklyBlocksMin()) {
      throw patchMapper.invalid("weeklyBlocksMax");
    }
    if (patch.containsKey("preferences")) {
      course.setPreferences(patchMapper.object(patch, "preferences"));
    }
    return courses.save(course);
  }

  private PageResponse<?> page(Page<?> page) {
    return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  record CareerRequest(@NotBlank String code, @NotBlank String name, boolean active) {}

  record CourseRequest(
      @NotBlank String code,
      @NotBlank String name,
      boolean requiresLab,
      @Min(1) int weeklyBlocksMin,
      @Min(1) int weeklyBlocksMax,
      Map<String, Object> preferences) {
    @AssertTrue(message = "weeklyBlocksMax debe ser mayor o igual que weeklyBlocksMin.")
    boolean isBlockRangeValid() {
      return weeklyBlocksMax >= weeklyBlocksMin;
    }
  }
}
