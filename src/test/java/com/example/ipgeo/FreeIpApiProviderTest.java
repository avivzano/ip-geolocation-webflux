package com.example.ipgeo;

import com.example.ipgeo.config.AppProperties;
import com.example.ipgeo.provider.FreeIpApiProvider;
import com.example.ipgeo.ratelimit.RateLimiterService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FreeIpApiProviderTest {

  private MockWebServer mockWebServer;
  private WebClient webClient;

  private RetryRegistry noRetryRegistry;
  private CircuitBreakerRegistry cbRegistry;

  private FreeIpApiProvider providerNoRetry;

  @Mock private RateLimiterService rateLimiterService;
  @Mock private AppProperties appProperties;

  @BeforeEach
  void setUp() throws IOException {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    webClient = WebClient.builder()
            .baseUrl(mockWebServer.url("/").toString())
            .build();

    noRetryRegistry = RetryRegistry.of(
            RetryConfig.custom().maxAttempts(1).waitDuration(Duration.ZERO).build());
    cbRegistry = CircuitBreakerRegistry.ofDefaults();

    when(appProperties.getFreeipapi()).thenReturn(freeIpProps(false, "geoApiRetry", "geoApiBreaker"));

    providerNoRetry = new FreeIpApiProvider(
            webClient,
            rateLimiterService,
            noRetryRegistry,
            cbRegistry,
            appProperties
    );
  }

  @AfterEach
  void tearDown() throws IOException {
    mockWebServer.shutdown();
  }

  @Test
  @DisplayName("200 OK → maps JSON to GeoLocationResult")
  void shouldFetchGeoLocationSuccessfully() throws InterruptedException {
    String testIp = "136.159.0.0";
    String responseBody = """
        {
          "continent": "North America",
          "countryName": "Canada",
          "regionName": "Alberta",
          "cityName": "Calgary",
          "latitude": 51.075153,
          "longitude": -114.12841
        }
        """;

    mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(responseBody)
            .addHeader("Content-Type", "application/json"));

    StepVerifier.create(providerNoRetry.fetch(testIp))
            .assertNext(result -> {
              assertThat(result.ipAddress()).isEqualTo(testIp);
              assertThat(result.continentName()).isEqualTo("North America");
              assertThat(result.countryName()).isEqualTo("Canada");
              assertThat(result.regionName()).isEqualTo("Alberta");
              assertThat(result.cityName()).isEqualTo("Calgary");
              assertThat(result.latitude()).isEqualTo(51.075153);
              assertThat(result.longitude()).isEqualTo(-114.12841);
            })
            .verifyComplete();

    RecordedRequest req = mockWebServer.takeRequest();
    assertThat(req.getMethod()).isEqualTo("GET");
    assertThat(req.getPath()).isEqualTo("/" + testIp);
  }

  @Test
  @DisplayName("429 Too Many Requests → propagates HTTP error")
  void shouldHandle429Error() {
    String testIp = "1.2.3.4";
    mockWebServer.enqueue(new MockResponse().setResponseCode(429).setBody("Too Many Requests"));

    StepVerifier.create(providerNoRetry.fetch(testIp))
            .expectErrorMatches(err -> err.getMessage() != null && err.getMessage().contains("429"))
            .verify();
  }

  @Test
  @DisplayName("500 Server Error → propagates HTTP error")
  void shouldHandle500Error() {
    String testIp = "1.2.3.4";
    mockWebServer.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

    StepVerifier.create(providerNoRetry.fetch(testIp))
            .expectErrorMatches(err -> err.getMessage() != null && err.getMessage().contains("500"))
            .verify();
  }

  @Test
  @DisplayName("Malformed JSON → decoding error")
  void shouldHandleMalformedJson() {
    String testIp = "1.2.3.4";
    mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody("not-json")
            .addHeader("Content-Type", "application/json"));

    StepVerifier.create(providerNoRetry.fetch(testIp))
            .expectError()
            .verify();
  }

  @Test
  @DisplayName("Rate limiter enabled → still returns success")
  void shouldApplyRateLimitingWhenEnabled() throws InterruptedException {
    String testIp = "1.2.3.4";
    String responseBody = """
        {
          "continent": "Europe",
          "countryName": "Germany",
          "regionName": "Bavaria",
          "cityName": "Munich",
          "latitude": 48.1351,
          "longitude": 11.5820
        }
        """;

    mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(responseBody)
            .addHeader("Content-Type", "application/json"));

    AppProperties.Freeipapi cfg = freeIpProps(true, "geoApiRetry", "geoApiBreaker");
    when(appProperties.getFreeipapi()).thenReturn(cfg);

    RateLimiterConfig rlCfg = RateLimiterConfig.custom()
            .limitForPeriod(10)
            .limitRefreshPeriod(Duration.ofSeconds(1))
            .timeoutDuration(Duration.ofMillis(50))
            .build();
    RateLimiter rl = RateLimiter.of("geoApiLimiter", rlCfg);

    when(rateLimiterService.isEnabled()).thenReturn(true);
    when(rateLimiterService.get()).thenReturn(rl);

    StepVerifier.create(providerNoRetry.fetch(testIp))
            .assertNext(result ->
                    assertThat(result.countryName()).isEqualTo("Germany"))
            .verifyComplete();

    RecordedRequest req = mockWebServer.takeRequest();
    assertThat(req.getPath()).isEqualTo("/" + testIp);
  }

  @Test
  @DisplayName("Network disconnect then success → retried and succeeds")
  void shouldRetryOnNetworkFailureAndSucceed() {
    String testIp = "8.8.8.8";
    String successResponse = """
        {
          "continent": "North America",
          "countryName": "United States",
          "regionName": "California",
          "cityName": "Mountain View",
          "latitude": 37.4056,
          "longitude": -122.0775
        }
        """;

    mockWebServer.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST));
    mockWebServer.enqueue(new MockResponse()
            .setResponseCode(200)
            .setBody(successResponse)
            .addHeader("Content-Type", "application/json"));

    RetryRegistry retryTwice = RetryRegistry.of(
            RetryConfig.custom().maxAttempts(2).waitDuration(Duration.ofMillis(50)).build());

    FreeIpApiProvider providerRetrying = new FreeIpApiProvider(
            webClient,
            rateLimiterService,
            retryTwice,
            cbRegistry,
            appProperties
    );

    when(appProperties.getFreeipapi()).thenReturn(freeIpProps(false, "geoApiRetry", "geoApiBreaker"));

    StepVerifier.create(providerRetrying.fetch(testIp))
            .assertNext(result -> {
              assertThat(result.countryName()).isEqualTo("United States");
              assertThat(result.cityName()).isEqualTo("Mountain View");
            })
            .verifyComplete();

    assertThat(mockWebServer.getRequestCount()).isEqualTo(2);
  }

  private AppProperties.Freeipapi freeIpProps(boolean rlEnabled, String retryName, String breakerName) {
    AppProperties.Freeipapi cfg = new AppProperties.Freeipapi();
    cfg.setBaseUrl("http://unused-in-test");
    cfg.setTimeout(Duration.ofSeconds(2));
    cfg.setConnectTimeout(Duration.ofSeconds(1));
    cfg.getRatelimiter().setEnabled(rlEnabled);
    cfg.getRatelimiter().setName("geoApiLimiter");
    cfg.getRetry().setName(retryName);
    cfg.getCircuitbreaker().setName(breakerName);
    return cfg;
  }
}
