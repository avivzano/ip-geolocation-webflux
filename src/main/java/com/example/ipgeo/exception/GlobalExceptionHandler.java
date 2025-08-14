package com.example.ipgeo.exception;
import com.example.ipgeo.config.AppProperties;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.reactive.function.client.WebClientResponseException;
@ControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {
  private final AppProperties props;
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<String> badRequest(IllegalArgumentException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
  }
  @ExceptionHandler(RequestNotPermitted.class)
  public ResponseEntity<String> tooMany(RequestNotPermitted ex) {
    HttpHeaders h = new HttpHeaders();
    h.add("Retry-After", String.valueOf(props.getBackpressure().getRetryAfterSeconds()));
    return new ResponseEntity<>("Too Many Requests - please retry later", h, HttpStatus.TOO_MANY_REQUESTS);
  }
  @ExceptionHandler(WebClientResponseException.class)
  public ResponseEntity<String> upstream(WebClientResponseException ex) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body("Upstream provider error: " + ex.getMessage());
  }
}