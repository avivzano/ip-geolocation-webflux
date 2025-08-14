package com.example.ipgeo.service;
import com.example.ipgeo.model.GeoLocationResult;
import reactor.core.publisher.Mono;
public interface GeoLocationService {
  Mono<GeoLocationResult> locate(String ipAddress);
}