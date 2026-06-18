package edu.udeo.horarios.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.udeo.horarios.api.security.JwtService.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
  private final JwtService jwtService;
  private final AppUserRepository users;
  private final ObjectMapper objectMapper;
  private final Clock clock;

  JwtAuthFilter(JwtService jwtService, AppUserRepository users, ObjectMapper objectMapper, Clock clock) {
    this.jwtService = jwtService;
    this.users = users;
    this.objectMapper = objectMapper;
    this.clock = clock;
  }

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    String header = request.getHeader("Authorization");
    if (header == null || !header.startsWith("Bearer ")) {
      chain.doFilter(request, response);
      return;
    }
    try {
      long userId = jwtService.verify(header.substring(7), clock.instant()).userId();
      AppUser user = users.findById(userId).filter(AppUser::active).orElseThrow(() -> new JwtException("AUTH_TOKEN_INVALID"));
      var auth =
          new UsernamePasswordAuthenticationToken(
              user, null, java.util.List.of(new SimpleGrantedAuthority("ROLE_" + user.role().name())));
      SecurityContextHolder.getContext().setAuthentication(auth);
      chain.doFilter(request, response);
    } catch (JwtException exception) {
      SecurityContextHolder.clearContext();
      response.setStatus(HttpStatus.UNAUTHORIZED.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      String message =
          "AUTH_TOKEN_EXPIRED".equals(exception.code()) ? "Token expirado." : "Token invalido.";
      objectMapper.writeValue(response.getWriter(), new SecurityErrorResponse(exception.code(), message));
    }
  }
}
