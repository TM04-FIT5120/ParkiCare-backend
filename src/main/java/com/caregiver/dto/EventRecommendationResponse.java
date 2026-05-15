package com.caregiver.dto;

import lombok.Data;

import java.util.List;

@Data
public class EventRecommendationResponse {

    private List<EventRecommendationItem> eventRecommendations;
}
