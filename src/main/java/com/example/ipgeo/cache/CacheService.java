package com.example.ipgeo.cache;
import com.example.ipgeo.model.GeoLocationResult;
import java.util.Optional;
public interface CacheService {
  Optional<GeoLocationResult> get(String ipAddress);
  void put(String ipAddress, GeoLocationResult result);
}