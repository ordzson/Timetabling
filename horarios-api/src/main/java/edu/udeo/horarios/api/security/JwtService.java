package edu.udeo.horarios.api.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class JwtService {
  private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
  private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
  private final ObjectMapper objectMapper;
  private final byte[] secret;
  private final long ttlSeconds;

  JwtService(
      ObjectMapper objectMapper,
      @Value("${horarios.auth.jwt-secret:dev-secret-change-me}") String secret,
      @Value("${horarios.auth.jwt-ttl-seconds:3600}") long ttlSeconds) {
    this.objectMapper = objectMapper;
    this.secret = secret.getBytes(StandardCharsets.UTF_8);
    this.ttlSeconds = ttlSeconds;
  }

  IssuedToken issue(AppUser user, Instant now) {
    Instant expiresAt = now.plusSeconds(ttlSeconds);
    String header = encodeJson(Map.of("alg", "HS256", "typ", "JWT"));
    String payload =
        encodeJson(
            Map.of(
                "sub", String.valueOf(user.id()),
                "email", user.email(),
                "role", user.role().name(),
                "exp", expiresAt.getEpochSecond()));
    String unsigned = header + "." + payload;
    return new IssuedToken(unsigned + "." + sign(unsigned), expiresAt);
  }

  JwtClaims verify(String token, Instant now) {
    String[] parts = token.split("\\.", -1);
    if (parts.length != 3) {
      throw new JwtException("AUTH_TOKEN_INVALID");
    }
    String unsigned = parts[0] + "." + parts[1];
    if (!sign(unsigned).equals(parts[2])) {
      throw new JwtException("AUTH_TOKEN_INVALID");
    }
    try {
      Map<String, Object> payload =
          objectMapper.readValue(DECODER.decode(parts[1]), new TypeReference<Map<String, Object>>() {});
      long exp = ((Number) payload.get("exp")).longValue();
      if (now.getEpochSecond() >= exp) {
        throw new JwtException("AUTH_TOKEN_EXPIRED");
      }
      return new JwtClaims(Long.parseLong((String) payload.get("sub")));
    } catch (JwtException exception) {
      throw exception;
    } catch (RuntimeException | java.io.IOException exception) {
      throw new JwtException("AUTH_TOKEN_INVALID");
    }
  }

  private String encodeJson(Map<String, Object> value) {
    try {
      return ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    } catch (java.io.IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private String sign(String value) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret, "HmacSHA256"));
      return ENCODER.encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException(exception);
    }
  }

  record IssuedToken(String value, Instant expiresAt) {
  }

  record JwtClaims(long userId) {
  }

  static class JwtException extends RuntimeException {
    private final String code;

    JwtException(String code) {
      this.code = code;
    }

    String code() {
      return code;
    }
  }
}
