package com.example.ipgeo.provider;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
@JsonIgnoreProperties(ignoreUnknown = true)
public class FreeIpApiDto {
  @JsonProperty("continent") public String continent;
  @JsonProperty("countryName") public String countryName;
  @JsonProperty("regionName") public String regionName;
  @JsonProperty("cityName") public String cityName;
  @JsonProperty("latitude") public double latitude;
  @JsonProperty("longitude") public double longitude;
}