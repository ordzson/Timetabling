package edu.udeo.horarios.api.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
class AuthController {
  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtService jwtService;
  private final Clock clock;

  AuthController(AppUserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService, Clock clock) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.jwtService = jwtService;
    this.clock = clock;
  }

  @PostMapping("/auth/login")
  LoginResponse login(@Valid @RequestBody LoginRequest request) {
    AppUser user =
        users
            .findByEmail(request.email())
            .filter(AppUser::active)
            .filter(found -> passwordEncoder.matches(request.password(), found.passwordHash()))
            .orElseThrow(() -> new InvalidCredentialsException());
    JwtService.IssuedToken token = jwtService.issue(user, clock.instant());
    return new LoginResponse(token.value(), "Bearer", token.expiresAt(), AppUserDto.from(user));
  }

  @GetMapping("/me")
  AppUserDto me(@AuthenticationPrincipal AppUser user) {
    return AppUserDto.from(user);
  }

  record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
  }

  record LoginResponse(String accessToken, String tokenType, Instant expiresAt, AppUserDto user) {
  }

  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  static class InvalidCredentialsException extends ResponseStatusException {
    InvalidCredentialsException() {
      super(HttpStatus.UNAUTHORIZED, "AUTH_INVALID_CREDENTIALS");
    }
  }
}
