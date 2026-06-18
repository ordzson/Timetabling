package edu.udeo.horarios.api.catalog.teacher;

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
@Table(name = "teacher")
public class TeacherEntity {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private String code;
  private String fullName;
  private int priority;
  private int minCourses = 1;
  private int maxCourses = 6;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private Map<String, Object> preferences = Map.of();

  private boolean active = true;

  public Long getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getFullName() {
    return fullName;
  }

  public void setFullName(String fullName) {
    this.fullName = fullName;
  }

  public int getPriority() {
    return priority;
  }

  public void setPriority(int priority) {
    this.priority = priority;
  }

  public int getMinCourses() {
    return minCourses;
  }

  public void setMinCourses(int minCourses) {
    this.minCourses = minCourses;
  }

  public int getMaxCourses() {
    return maxCourses;
  }

  public void setMaxCourses(int maxCourses) {
    this.maxCourses = maxCourses;
  }

  public Map<String, Object> getPreferences() {
    return preferences;
  }

  public void setPreferences(Map<String, Object> preferences) {
    this.preferences = preferences == null ? Map.of() : preferences;
  }

  public boolean isActive() {
    return active;
  }

  public void setActive(boolean active) {
    this.active = active;
  }
}
