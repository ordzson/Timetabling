package edu.udeo.horarios.api.security;

record AppUser(
    long id,
    String email,
    String passwordHash,
    String fullName,
    AppRole role,
    Long teacherId,
    Long cohortId,
    boolean active) {
}
