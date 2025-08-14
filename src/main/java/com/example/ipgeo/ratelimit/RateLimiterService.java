package com.example.ipgeo.ratelimit;

import io.github.resilience4j.ratelimiter.RateLimiter;

public interface RateLimiterService {
  RateLimiter get();
  boolean isEnabled();
}
