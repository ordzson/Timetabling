package edu.udeo.horarios.api.security;

record AppUserDto(
    long id,
    String email,
    String fullName,
    AppRole role,
    Long teacherId,
    Long cohortId,
    boolean active) {
  static AppUserDto from(AppUser user) {
    return new AppUserDto(
        user.id(), user.email(), user.fullName(), user.role(), user.teacherId(), user.cohortId(), user.active());
  }
}
