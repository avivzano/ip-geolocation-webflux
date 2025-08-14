package com.example.ipgeo.controller;

import com.example.ipgeo.model.GeoLocationResult;
import com.example.ipgeo.service.GeoLocationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@Slf4j
@RestController
@RequestMapping("/ip")
@RequiredArgsConstructor
public class GeoLocationController {

  private final GeoLocationService service;

  @GetMapping(produces = "application/json")
  public Mono<GeoLocationResult> locate(@RequestParam String address) {
    log.info("Received IP lookup request for address: {}", address);

    return service.locate(address)
            .doOnNext(result -> log.info("Successfully retrieved geo data: {}", result))
            .doOnError(error -> log.error("Error retrieving geo data for {}: {}", address, error.getMessage()));
  }
}
