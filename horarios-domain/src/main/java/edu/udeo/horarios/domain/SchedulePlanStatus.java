package edu.udeo.horarios.domain;

public enum SchedulePlanStatus {
  DRAFT,
  VALIDATING,
  INVALID_INPUT,
  GENERATING,
  GENERATED,
  GENERATED_WITH_CONFLICTS,
  APPROVED,
  LOCKED,
  ARCHIVED
}
