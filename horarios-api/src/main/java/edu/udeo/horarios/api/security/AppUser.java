package edu.udeo.horarios.api.security;

public record AppUser(
    long id,
    String email,
    String passwordHash,
    String fullName,
    AppRole role,
    Long teacherId,
    Long cohortId,
    boolean active) {
}
