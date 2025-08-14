package com.example.ipgeo;

import com.example.ipgeo.cache.CacheService;
import com.example.ipgeo.model.GeoLocationResult;
import com.example.ipgeo.provider.GeoProvider;
import com.example.ipgeo.service.GeoLocationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for GeoLocationServiceImpl (pure service slice, no Spring context).
 */
@ExtendWith(MockitoExtension.class)
class GeoLocationServiceImplTest {

  @Mock
  private GeoProvider geoProvider;

  @Test
  @DisplayName("cache hit → returns cached result and does not call provider")
  void cacheHit() {
    FakeCache cache = new FakeCache();
    String ip = "1.1.1.1";
    GeoLocationResult cached = new GeoLocationResult(ip, "A", "B", "C", "D", 1.0, 2.0);
    cache.put(ip, cached);

    GeoLocationServiceImpl service = new GeoLocationServiceImpl(cache, geoProvider);

    StepVerifier.create(service.locate(ip))
            .expectNext(cached)
            .verifyComplete();

    verifyNoInteractions(geoProvider);
  }

  @Test
  @DisplayName("cache miss → calls provider, stores result in cache")
  void cacheMissStoresInCache() {
    FakeCache cache = new FakeCache();
    String ip = "8.8.8.8";
    GeoLocationResult api = new GeoLocationResult(ip, "NA", "US", "CA", "Mountain View", 37.4056, -122.0775);

    when(geoProvider.fetch(ip)).thenReturn(Mono.just(api));

    GeoLocationServiceImpl service = new GeoLocationServiceImpl(cache, geoProvider);

    StepVerifier.create(service.locate(ip))
            .expectNext(api)
            .verifyComplete();

    verify(geoProvider, times(1)).fetch(ip);
    assertThat(cache.get(ip)).contains(api);
  }

  @Test
  @DisplayName("concurrent callers for same IP → only one provider call (coalescing)")
  void concurrentRequestsCoalesce() throws InterruptedException {
    FakeCache cache = new FakeCache();
    String ip = "9.9.9.9";
    GeoLocationResult api = new GeoLocationResult(ip, "EU", "DE", "BY", "Munich", 48.1351, 11.5820);

    Sinks.One<GeoLocationResult> sink = Sinks.one();
    when(geoProvider.fetch(ip)).thenReturn(sink.asMono());

    GeoLocationServiceImpl service = new GeoLocationServiceImpl(cache, geoProvider);

    Mono<GeoLocationResult> first = service.locate(ip);
    Mono<GeoLocationResult> second = service.locate(ip);

    CountDownLatch latch = new CountDownLatch(2);
    GeoLocationResult[] got = new GeoLocationResult[2];

    first.subscribe(r -> { got[0] = r; latch.countDown(); });
    second.subscribe(r -> { got[1] = r; latch.countDown(); });

    sink.tryEmitValue(api);

    boolean completed = latch.await(1, TimeUnit.SECONDS);
    assertThat(completed).isTrue();
    assertThat(got[0]).isEqualTo(api);
    assertThat(got[1]).isEqualTo(api);

    verify(geoProvider, times(1)).fetch(ip);
    assertThat(cache.get(ip)).contains(api);
  }

  @Test
  @DisplayName("invalid IP → IllegalArgumentException")
  void invalidIp() {
    FakeCache cache = new FakeCache();
    GeoLocationServiceImpl service = new GeoLocationServiceImpl(cache, geoProvider);

    StepVerifier.create(service.locate("not-an-ip"))
            .expectErrorSatisfies(err -> {
              assertThat(err).isInstanceOf(IllegalArgumentException.class);
              assertThat(err).hasMessage("Invalid IP address format");
            })
            .verify();

    verifyNoInteractions(geoProvider);
  }

  @Test
  @DisplayName("valid IPv6 → calls provider")
  void validIpv6() {
    FakeCache cache = new FakeCache();
    String ip = "2001:db8::1";
    GeoLocationResult api = new GeoLocationResult(ip, "Test", "X", "Y", "Z", 0.0, 0.0);

    when(geoProvider.fetch(ip)).thenReturn(Mono.just(api).delayElement(Duration.ofMillis(10)));

    GeoLocationServiceImpl service = new GeoLocationServiceImpl(cache, geoProvider);

    StepVerifier.create(service.locate(ip))
            .expectNext(api)
            .verifyComplete();

    verify(geoProvider, times(1)).fetch(ip);
  }

  @Test
  @DisplayName("after provider completes, next call is served from cache (no provider call)")
  void subsequentCallServedFromCache() {
    FakeCache cache = new FakeCache();
    String ip = "4.4.4.4";
    GeoLocationResult api = new GeoLocationResult(ip, "X", "Y", "Z", "W", 10.0, 20.0);

    when(geoProvider.fetch(ip)).thenReturn(Mono.just(api));

    GeoLocationServiceImpl service = new GeoLocationServiceImpl(cache, geoProvider);

    StepVerifier.create(service.locate(ip)).expectNext(api).verifyComplete();
    verify(geoProvider, times(1)).fetch(ip);

    // second call should be served from cache
    StepVerifier.create(service.locate(ip)).expectNext(api).verifyComplete();
    verifyNoMoreInteractions(geoProvider);
  }

  /** Minimal in-memory CacheService for tests. */
  private static class FakeCache implements CacheService {
    private final ConcurrentHashMap<String, GeoLocationResult> map = new ConcurrentHashMap<>();
    @Override public Optional<GeoLocationResult> get(String key) { return Optional.ofNullable(map.get(key)); }
    @Override public void put(String key, GeoLocationResult value) { map.put(key, value); }
  }
}
