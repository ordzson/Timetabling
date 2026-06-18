package edu.udeo.horarios.api.catalog.academic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "course")
public class CourseEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String code;
  private String name;
  private boolean requiresLab;
  private int weeklyBlocksMin;
  private int weeklyBlocksMax;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> preferences = Map.of();

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public boolean isRequiresLab() {
    return requiresLab;
  }

  public void setRequiresLab(boolean requiresLab) {
    this.requiresLab = requiresLab;
  }

  public int getWeeklyBlocksMin() {
    return weeklyBlocksMin;
  }

  public void setWeeklyBlocksMin(int weeklyBlocksMin) {
    this.weeklyBlocksMin = weeklyBlocksMin;
  }

  public int getWeeklyBlocksMax() {
    return weeklyBlocksMax;
  }

  public void setWeeklyBlocksMax(int weeklyBlocksMax) {
    this.weeklyBlocksMax = weeklyBlocksMax;
  }

  public Map<String, Object> getPreferences() {
    return preferences;
  }

  public void setPreferences(Map<String, Object> preferences) {
    this.preferences = preferences == null ? Map.of() : preferences;
  }
}
