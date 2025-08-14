package com.example.ipgeo;

import com.example.ipgeo.controller.GeoLocationController;
import com.example.ipgeo.exception.GlobalExceptionHandler;
import com.example.ipgeo.model.GeoLocationResult;
import com.example.ipgeo.service.GeoLocationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@WebFluxTest(
        controllers = GeoLocationController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.ASSIGNABLE_TYPE,
                classes = GlobalExceptionHandler.class // exclude prod advice that depends on AppProperties
        )
)
@Import(GeoLocationControllerTest.TestErrorHandler.class)
class GeoLocationControllerTest {

  @Autowired
  private WebTestClient webTestClient;

  @MockBean
  private GeoLocationService service;

  @Test
  @DisplayName("GET /ip?address=1.1.1.1 → 200 + JSON body")
  void happyPath() {
    GeoLocationResult r = new GeoLocationResult("1.1.1.1", "A", "B", "C", "D", 1.0, 2.0);
    when(service.locate("1.1.1.1")).thenReturn(Mono.just(r));

    webTestClient.get()
            .uri("/ip?address=1.1.1.1")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk()
            .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.IpAddress").isEqualTo("1.1.1.1")
            .jsonPath("$.Continent").isEqualTo("A")
            .jsonPath("$.Country").isEqualTo("B")
            .jsonPath("$.Region").isEqualTo("C")
            .jsonPath("$.City").isEqualTo("D")
            .jsonPath("$.Latitude").isEqualTo(1.0)
            .jsonPath("$.Longitude").isEqualTo(2.0);
  }

  @Test
  @DisplayName("GET /ip?address=not-an-ip → 400 with message")
  void invalidIpReturns400() {
    when(service.locate(anyString()))
            .thenReturn(Mono.error(new IllegalArgumentException("Invalid IP address format")));

    webTestClient.get()
            .uri("/ip?address=not-an-ip")
            .exchange()
            .expectStatus().isBadRequest()
            .expectBody(String.class).isEqualTo("Invalid IP address format");
  }

  @Test
  @DisplayName("GET /ip?address=2.2.2.2 → 429 with Retry-After header")
  void rateLimitedReturns429WithRetryAfter() {
    when(service.locate(anyString()))
            .thenReturn(Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Rate limited")));

    webTestClient.get()
            .uri("/ip?address=2.2.2.2")
            .exchange()
            .expectStatus().isEqualTo(429)
            .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "1");
  }

  @Test
  @DisplayName("GET /ip (missing address) → 400")
  void missingAddressParamReturns400() {
    webTestClient.get()
            .uri("/ip")
            .exchange()
            .expectStatus().isBadRequest();
  }

  @RestControllerAdvice
  static class TestErrorHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    Mono<ResponseEntity<String>> badRequest(IllegalArgumentException ex) {
      return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    Mono<ResponseEntity<Void>> status(ResponseStatusException ex) {
      HttpHeaders headers = new HttpHeaders();
      if (ex.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
        headers.set(HttpHeaders.RETRY_AFTER, "1");
      }
      return Mono.just(new ResponseEntity<>(headers, ex.getStatusCode()));
    }
  }
}
