package com.caregiver.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLanguageRequest {
    @Pattern(regexp = "^(en|zh-CN|ms-MY)$", message = "Language must be en, zh-CN, or ms-MY")
    private String language;
}
