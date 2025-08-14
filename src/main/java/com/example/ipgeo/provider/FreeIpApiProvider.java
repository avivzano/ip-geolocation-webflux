package com.example.ipgeo.provider;

import com.example.ipgeo.config.AppProperties;
import com.example.ipgeo.model.GeoLocationResult;
import com.example.ipgeo.ratelimit.RateLimiterService;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.ratelimiter.operator.RateLimiterOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class FreeIpApiProvider implements GeoProvider {

  private final WebClient freeIpApiWebClient;
  private final RateLimiterService rateLimiterService;
  private final RetryRegistry retryRegistry;
  private final CircuitBreakerRegistry circuitBreakerRegistry;
  private final AppProperties props;

  @Override
  public Mono<GeoLocationResult> fetch(String ip) {

    boolean rlEnabled = rateLimiterService.isEnabled();
    String retryName = props.getFreeipapi().getRetry().getName();
    String breakerName = props.getFreeipapi().getCircuitbreaker().getName();

    Mono<GeoLocationResult> call = callUpstream(ip);

    if (rlEnabled) {
      call = call.transformDeferred(RateLimiterOperator.of(rateLimiterService.get()));
    }

    return call
            .transformDeferred(CircuitBreakerOperator.of(circuitBreakerRegistry.circuitBreaker(breakerName)))
            .transformDeferred(RetryOperator.of(retryRegistry.retry(retryName)))
            .timeout(props.getFreeipapi().getTimeout())
            .doOnSubscribe(s -> log.debug("geo.fetch start ip={}", ip))
            .doOnSuccess(r -> log.debug("geo.fetch ok ip={}", ip))
            .doOnError(e -> log.warn("geo.fetch fail ip={} err={}", ip, e.toString()));
  }

  private Mono<GeoLocationResult> callUpstream(String ip) {
    return freeIpApiWebClient
            .get()
            .uri("/{ip}", ip)
            .exchangeToMono(resp -> resp.statusCode().is2xxSuccessful()
                    ? resp.bodyToMono(FreeIpApiDto.class).map(dto -> toResult(ip, dto))
                    : toError(resp, ip));
  }

  private Mono<GeoLocationResult> toError(ClientResponse resp, String ip) {
    return resp.bodyToMono(String.class)
            .defaultIfEmpty("")
            .flatMap(body -> {
              log.warn("upstream status={} ip={} body={}", resp.statusCode().value(), ip, truncate(body));
              return resp.createException().flatMap(Mono::error);
            });
  }

  private GeoLocationResult toResult(String ip, FreeIpApiDto dto) {
    return new GeoLocationResult(
            ip,
            nz(dto.continent), nz(dto.countryName), nz(dto.regionName), nz(dto.cityName),
            dto.latitude, dto.longitude
    );
  }

  private static String nz(String s) { return s == null ? "" : s; }
  private static String truncate(String s) { return s.length() <= 512 ? s : s.substring(0, 512) + "..."; }
}
