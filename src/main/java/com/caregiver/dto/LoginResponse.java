package com.caregiver.dto;

import com.caregiver.annotation.Translatable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class LoginResponse {
    private Long caregiverId;
    private String nickname;
    private String uniqueId;
    @Translatable
    private String message;
    private String language;
}