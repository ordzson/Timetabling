package edu.udeo.horarios.api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.udeo.horarios.api.security.JwtAuthFilter;
import edu.udeo.horarios.api.security.SecurityErrorResponse;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
class SecurityConfig {
  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthFilter jwtAuthFilter, ObjectMapper objectMapper)
      throws Exception {
    return http.csrf(csrf -> csrf.disable())
        .httpBasic(AbstractHttpConfigurer::disable)
        .formLogin(AbstractHttpConfigurer::disable)
        .logout(AbstractHttpConfigurer::disable)
        .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exceptions ->
                exceptions
                    .authenticationEntryPoint(
                        (request, response, exception) -> {
                          response.setStatus(HttpStatus.UNAUTHORIZED.value());
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          objectMapper.writeValue(
                              response.getWriter(),
                              new SecurityErrorResponse("AUTH_TOKEN_MISSING", "Falta bearer token."));
                        })
                    .accessDeniedHandler(
                        (request, response, exception) -> {
                          response.setStatus(HttpStatus.FORBIDDEN.value());
                          response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                          objectMapper.writeValue(
                              response.getWriter(),
                              new SecurityErrorResponse("FORBIDDEN", "Rol insuficiente o recurso ajeno."));
                        }))
        .authorizeHttpRequests(
            requests ->
                requests
                    .requestMatchers("/api/auth/login").permitAll()
                    .requestMatchers("/api/me").authenticated()
                    .requestMatchers("/api/catalog/**", "/api/imports/**", "/api/schedule-plans/**")
                    .hasAnyRole("SUPERADMIN", "ADMIN")
                    .requestMatchers("/api/teacher/**").hasRole("TEACHER")
                    .requestMatchers("/api/public/schedules/cohorts/**").hasAnyRole("SUPERADMIN", "ADMIN", "STUDENT")
                    .requestMatchers("/api/reports/**").hasAnyRole("SUPERADMIN", "ADMIN")
                    .requestMatchers("/api/substitutions/**").hasAnyRole("SUPERADMIN", "ADMIN", "TEACHER")
                    .anyRequest().authenticated())
        .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
        .build();
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  UserDetailsService userDetailsService() {
    return username -> {
      throw new UsernameNotFoundException(username);
    };
  }

  @Bean
  FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
    FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setEnabled(false);
    return registration;
  }
}
