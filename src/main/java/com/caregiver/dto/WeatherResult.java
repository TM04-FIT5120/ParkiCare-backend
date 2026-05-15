package com.caregiver.dto;

public class WeatherResult {

    private Double temperature;
    private String weather;
    private String weatherDesc;
    private Integer aqi;

    public WeatherResult() {
    }

    public WeatherResult(Double temperature, String weather, String weatherDesc, Integer aqi) {
        this.temperature = temperature;
        this.weather = weather;
        this.weatherDesc = weatherDesc;
        this.aqi = aqi;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public String getWeather() {
        return weather;
    }

    public void setWeather(String weather) {
        this.weather = weather;
    }

    public String getWeatherDesc() {
        return weatherDesc;
    }

    public void setWeatherDesc(String weatherDesc) {
        this.weatherDesc = weatherDesc;
    }

    public Integer getAqi() {
        return aqi;
    }

    public void setAqi(Integer aqi) {
        this.aqi = aqi;
    }
}
