# IP Geolocation (Spring WebFlux)

A small Spring WebFlux service that returns geolocation for an IP via FreeIPAPI.
It validates IPv4/IPv6, caches results for 30 days, deduplicates concurrent lookups, and protects the upstream with rate limiting, retries (with jitter), and a circuit breaker.

## Prerequisites
- JDK 21+ (JDK 22 is fine)
- Maven 3.9+
- Internet access (for real calls to FreeIPAPI)

## Build & Run
```bash
# Build and run tests, then install to local repo
mvn clean install

# Start the app (choose one)
mvn spring-boot:run
# or
java -jar target/*.jar
```
The service listens on **http://localhost:8080**.


## Quick checks (curl)
```bash
curl -i "http://localhost:8080/ip?address=1.1.1.1"        # IPv4
curl -i "http://localhost:8080/ip?address=2001:db8::1"    # IPv6
curl -i "http://localhost:8080/ip?address=not-an-ip"      # invalid -> 400

# First call hits the provider; subsequent calls served from cache
for i in {1..5}; do
  curl -s -o /dev/null -w "req=$i status=%{http_code} time=%{time_total}s\n"     "http://localhost:8080/ip?address=8.8.8.8"
done
```

## Configuration
All settings are in `src/main/resources/application.yml` (already set to sane defaults). You can override at runtime


## Tests
```bash
mvn test
```

## Notes
- FreeIPAPI’s free tier documents **60 requests/minute**. The default config matches that. If you prefer strictly **1 request/second**, you can adjust the Resilience4j rate limiter accordingly.
