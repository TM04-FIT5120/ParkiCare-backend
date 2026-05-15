package com.caregiver.dto;

import lombok.Data;

@Data
public class EventRecommendationItem {

    private String eventName;
    private String period;
    private String type;
    private String startTime;
    private Integer durationMinutes;
    private String remark;
}
