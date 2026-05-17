package com.caregiver.dto;

import com.caregiver.annotation.Translatable;
import com.caregiver.entity.DrugBase;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Outbound DTO for the medication catalog so caregiver-facing fields run
 * through the response translation advice. The dosage string is kept
 * untranslated to preserve clinical units (e.g. "100mg").
 */
@Data
@NoArgsConstructor
public class DrugResponse {

    private Long drugId;

    @Translatable
    private String drugName;

    private BigDecimal price;

    private String manufacturerName;

    private String dosage;

    private String frequency;

    private Integer isValid;

    @Translatable
    private String remark;

    private Integer intervalMinutes;

    public static DrugResponse from(DrugBase entity) {
        if (entity == null) return null;
        DrugResponse dto = new DrugResponse();
        dto.setDrugId(entity.getDrugId());
        dto.setDrugName(entity.getDrugName());
        dto.setPrice(entity.getPrice());
        dto.setManufacturerName(entity.getManufacturerName());
        dto.setDosage(entity.getDosage());
        dto.setFrequency(entity.getFrequency());
        dto.setIsValid(entity.getIsValid());
        dto.setRemark(entity.getRemark());
        dto.setIntervalMinutes(entity.getIntervalMinutes());
        return dto;
    }
}
