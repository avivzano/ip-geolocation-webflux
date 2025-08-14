package com.example.ipgeo.service;

import com.example.ipgeo.cache.CacheService;
import com.example.ipgeo.model.GeoLocationResult;
import com.example.ipgeo.provider.GeoProvider;
import com.google.common.net.InetAddresses;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class GeoLocationServiceImpl implements GeoLocationService {

  private final CacheService cacheService;
  private final GeoProvider geoProvider;
  private final Map<String, Mono<GeoLocationResult>> ongoingLookups = new ConcurrentHashMap<>();

  @Override
  public Mono<GeoLocationResult> locate(String ipAddress) {
    if (!isValidIp(ipAddress)) {
      return Mono.error(new IllegalArgumentException("Invalid IP address format"));
    }

    return Mono.justOrEmpty(cacheService.get(ipAddress))
            .switchIfEmpty(Mono.defer(() -> startOrJoinLookup(ipAddress)));
  }

  private Mono<GeoLocationResult> startOrJoinLookup(String ip) {
    return ongoingLookups.computeIfAbsent(ip, key ->
            Mono.defer(() ->
                    geoProvider.fetch(ip)
                            .doOnNext(result -> cacheService.put(ip, result))
                            .doFinally(sig -> ongoingLookups.remove(key))
            ).cache()
    );
  }

  private boolean isValidIp(String ip) {
    return InetAddresses.isInetAddress(ip);
  }
}
