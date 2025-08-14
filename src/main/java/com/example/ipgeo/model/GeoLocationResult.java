package com.example.ipgeo.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GeoLocationResult(
        @JsonProperty("IpAddress") String ipAddress,
        @JsonProperty("Continent") String continentName,
        @JsonProperty("Country") String countryName,
        @JsonProperty("Region") String regionName,
        @JsonProperty("City") String cityName,
        @JsonProperty("Latitude") Double latitude,
        @JsonProperty("Longitude") Double longitude
) {}
