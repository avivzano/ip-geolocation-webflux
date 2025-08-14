package com.example.ipgeo.cache;
import com.example.ipgeo.config.AppProperties;
import com.example.ipgeo.model.GeoLocationResult;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.Duration;
import java.util.Optional;
@Component
@RequiredArgsConstructor
public class CaffeineCacheService implements CacheService {
  private final AppProperties props;
  private Cache<String, GeoLocationResult> cache;
  private Cache<String, GeoLocationResult> cache() {
    if (cache == null) {
      cache = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofDays(props.getCache().getTtlDays()))
        .maximumSize(props.getCache().getMaxSize())
        .build();
    }
    return cache;
  }
  @Override public Optional<GeoLocationResult> get(String ipAddress) {
    return Optional.ofNullable(cache().getIfPresent(ipAddress));
  }
  @Override public void put(String ipAddress, GeoLocationResult result) {
    cache().put(ipAddress, result);
  }
}