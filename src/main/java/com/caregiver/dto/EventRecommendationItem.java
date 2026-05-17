package com.caregiver.dto;

import com.caregiver.annotation.Translatable;
import lombok.Data;

@Data
public class EventRecommendationItem {

    @Translatable
    private String eventName;
    private String period;
    private String type;
    private String startTime;
    private Integer durationMinutes;
    @Translatable
    private String remark;
}
