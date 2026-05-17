package com.caregiver.service;

import com.caregiver.dto.WeatherResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class WeatherService {

    @Value("${openweather.api.key}")
    private String openWeatherApiKey;

    @Value("${openweather.geo.url}")
    private String openWeatherGeoUrl;

    @Value("${openweather.weather.url}")
    private String openWeatherWeatherUrl;

    @Value("${waqi.api.key}")
    private String waqiApiKey;

    @Value("${waqi.api.url}")
    private String waqiApiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WeatherResult getByCity(String city) {
        if (city == null || city.trim().isEmpty()) {
            throw new RuntimeException("City cannot be empty");
        }

        try {
            String encodedCity = URLEncoder.encode(city.trim(), StandardCharsets.UTF_8);
            String geoUrl = openWeatherGeoUrl
                    + "?q=" + encodedCity
                    + "&limit=1"
                    + "&appid=" + openWeatherApiKey;

            ResponseEntity<String> geoResponse = restTemplate.getForEntity(geoUrl, String.class);
            JsonNode geoRoot = objectMapper.readTree(geoResponse.getBody());

            if (!geoRoot.isArray() || geoRoot.isEmpty()) {
                throw new RuntimeException("City not found");
            }

            JsonNode geoItem = geoRoot.get(0);
            double lat = geoItem.path("lat").asDouble();
            double lon = geoItem.path("lon").asDouble();

            return getWeatherAndAQI(lat, lon);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get weather by city: " + e.getMessage());
        }
    }

    public WeatherResult getWeatherAndAQI(double lat, double lon) {
        try {
            // --- AQI (WAQI) — non-fatal: null if key missing or station unavailable ---
            Integer aqi = null;
            double waqiTemperature = Double.NaN;
            if (waqiApiKey != null && !waqiApiKey.isBlank()) {
                try {
                    String waqiUrl = waqiApiUrl
                            + "/geo:" + lat + ";" + lon
                            + "/?token=" + waqiApiKey;
                    ResponseEntity<String> waqiResponse = restTemplate.getForEntity(waqiUrl, String.class);
                    JsonNode waqiRoot = objectMapper.readTree(waqiResponse.getBody());
                    if ("ok".equalsIgnoreCase(waqiRoot.path("status").asText())) {
                        JsonNode waqiData = waqiRoot.path("data");
                        int rawAqi = waqiData.path("aqi").asInt(-1);
                        if (rawAqi >= 0) {
                            aqi = rawAqi;
                        }
                        waqiTemperature = waqiData.path("iaqi").path("t").path("v").asDouble(Double.NaN);
                    }
                } catch (Exception ignored) {
                    // AQI unavailable — continue with OpenWeatherMap only
                }
            }

            // --- Weather + temperature (OpenWeatherMap) ---
            String weatherUrl = openWeatherWeatherUrl
                    + "?lat=" + lat
                    + "&lon=" + lon
                    + "&appid=" + openWeatherApiKey
                    + "&units=metric";

            ResponseEntity<String> weatherResponse = restTemplate.getForEntity(weatherUrl, String.class);
            JsonNode weatherRoot = objectMapper.readTree(weatherResponse.getBody());

            JsonNode weatherNode = weatherRoot.path("weather").isArray() && weatherRoot.path("weather").size() > 0
                    ? weatherRoot.path("weather").get(0)
                    : null;

            String weather = weatherNode != null ? weatherNode.path("main").asText("Unknown") : "Unknown";
            String weatherDesc = weatherNode != null ? weatherNode.path("description").asText("No weather description") : "No weather description";

            double temperature = Double.isNaN(waqiTemperature)
                    ? weatherRoot.path("main").path("temp").asDouble(Double.NaN)
                    : waqiTemperature;

            if (Double.isNaN(temperature)) {
                throw new RuntimeException("Temperature data not available");
            }

            return new WeatherResult(temperature, weather, weatherDesc, aqi);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to get weather and AQI: " + e.getMessage());
        }
    }

}
