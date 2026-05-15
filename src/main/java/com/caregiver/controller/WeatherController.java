package com.caregiver.controller;

import com.caregiver.dto.WeatherResult;
import com.caregiver.service.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WeatherController {

    private final WeatherService weatherService;

    @GetMapping("/city")
    public WeatherResult getWeatherByCity(@RequestParam String city) {
        return weatherService.getByCity(city);
    }

    @GetMapping("/coordinate")
    public WeatherResult getWeatherByCoordinate(@RequestParam double lat, @RequestParam double lon) {
        return weatherService.getWeatherAndAQI(lat, lon);
    }
}
