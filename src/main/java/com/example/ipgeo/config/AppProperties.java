package com.example.ipgeo.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import lombok.Data;
import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@Validated
@ConfigurationProperties(prefix = "ipgeo")
@Getter
public class AppProperties {

  private final Cache cache = new Cache();
  private final Backpressure backpressure = new Backpressure();
  private final Freeipapi freeipapi = new Freeipapi();

  @Data
  public static class Cache {
    @Positive
    private int ttlDays;
    @Positive
    private long maxSize;
  }

  @Data
  public static class Backpressure {
    @Min(0)
    private int retryAfterSeconds;
  }

  @Data
  public static class Freeipapi {
    @NotBlank
    private String baseUrl;
    @NotNull
    private Duration timeout;
    @NotNull
    private Duration connectTimeout;

    private final RateLimiterProperties ratelimiter = new RateLimiterProperties();
    private final NamedProperties retry = new NamedProperties();
    private final NamedProperties circuitbreaker = new NamedProperties();

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl != null && baseUrl.endsWith("/")
              ? baseUrl.substring(0, baseUrl.length() - 1)
              : baseUrl;
    }

    @Data
    public static class RateLimiterProperties {
      private boolean enabled;
      @NotBlank
      private String name;
    }

    @Data
    public static class NamedProperties {
      @NotBlank
      private String name;
    }
  }
}
