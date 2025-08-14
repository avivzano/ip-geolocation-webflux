package com.example.ipgeo.provider;
import com.example.ipgeo.model.GeoLocationResult;
import reactor.core.publisher.Mono;
public interface GeoProvider {
  Mono<GeoLocationResult> fetch(String ipAddress);
}