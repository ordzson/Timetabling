package edu.udeo.horarios.api.security;

import edu.udeo.horarios.api.security.AuthController.InvalidCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class AuthExceptionHandler {
  @ExceptionHandler(InvalidCredentialsException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  SecurityErrorResponse invalidCredentials() {
    return new SecurityErrorResponse("AUTH_INVALID_CREDENTIALS", "Email/password invalido.");
  }
}
