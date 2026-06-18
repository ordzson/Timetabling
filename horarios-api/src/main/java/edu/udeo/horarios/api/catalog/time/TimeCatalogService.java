package edu.udeo.horarios.api.catalog.time;

import edu.udeo.horarios.api.catalog.common.CatalogService;
import edu.udeo.horarios.api.catalog.common.NotFoundException;
import edu.udeo.horarios.api.catalog.common.PageResponse;
import edu.udeo.horarios.api.catalog.common.PatchMapper;
import edu.udeo.horarios.api.catalog.common.RequestMapper;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
class TimeCatalogService implements CatalogService {
  private final JourneyRepository journeys;
  private final RequestMapper mapper;
  private final PatchMapper patchMapper;

  TimeCatalogService(JourneyRepository journeys, RequestMapper mapper, PatchMapper patchMapper) {
    this.journeys = journeys;
    this.mapper = mapper;
    this.patchMapper = patchMapper;
  }

  @Override
  public Set<String> resources() {
    return Set.of("journeys");
  }

  @Override
  public Object create(String resource, Object request) {
    JourneyRequest dto = mapper.convert(request, JourneyRequest.class);
    if (journeys.existsByCode(dto.code())) {
      throw new DataIntegrityViolationException("duplicate journey code");
    }
    JourneyEntity journey = new JourneyEntity();
    journey.setCode(dto.code());
    journey.setName(dto.name());
    journey.setBlockMinutes(dto.blockMinutes());
    journey.setStartTime(dto.startTime());
    journey.setEndTime(dto.endTime());
    return journeys.save(journey);
  }

  @Override
  public Object patch(String resource, Long id, Object request) {
    JourneyEntity journey = journeys.findById(id).orElseThrow(() -> new NotFoundException("journey"));
    var patch = patchMapper.map(request, Set.of("code", "name", "blockMinutes", "startTime", "endTime"));
    if (patch.containsKey("code")) {
      String code = patchMapper.string(patch, "code");
      if (!code.equals(journey.getCode()) && journeys.existsByCode(code)) {
        throw new DataIntegrityViolationException("duplicate journey code");
      }
      journey.setCode(code);
    }
    if (patch.containsKey("name")) {
      journey.setName(patchMapper.string(patch, "name"));
    }
    if (patch.containsKey("blockMinutes")) {
      journey.setBlockMinutes(patchMapper.integer(patch, "blockMinutes"));
    }
    if (journey.getBlockMinutes() < 1) {
      throw patchMapper.invalid("blockMinutes");
    }
    if (patch.containsKey("startTime")) {
      journey.setStartTime(patchMapper.time(patch, "startTime"));
    }
    if (patch.containsKey("endTime")) {
      journey.setEndTime(patchMapper.time(patch, "endTime"));
    }
    if (!journey.getStartTime().isBefore(journey.getEndTime())) {
      throw patchMapper.invalid("endTime");
    }
    return journeys.save(journey);
  }

  @Override
  public PageResponse<?> list(String resource, Pageable pageable) {
    Page<JourneyEntity> page = journeys.findAll(pageable);
    return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  record JourneyRequest(
      @NotBlank String code,
      @NotBlank String name,
      @Min(1) int blockMinutes,
      @NotNull LocalTime startTime,
      @NotNull LocalTime endTime) {
    @AssertTrue(message = "endTime debe ser posterior a startTime.")
    boolean isTimeRangeValid() {
      return startTime == null || endTime == null || startTime.isBefore(endTime);
    }
  }
}
