package com.example.ipgeo.ratelimit;

import com.example.ipgeo.config.AppProperties;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RateLimiterServiceImpl implements RateLimiterService {

    private final RateLimiterRegistry registry;
    private final AppProperties props;

    @Override
    public RateLimiter get() {
        String name = props.getFreeipapi().getRatelimiter().getName();
        return registry.rateLimiter(name);
    }

    @Override
    public boolean isEnabled() {
        return props.getFreeipapi().getRatelimiter().isEnabled();
    }
}
