package com.caregiver.dto;

import lombok.Data;

import java.util.List;

@Data
public class MedicationReportDTO {

    private Long patientId;

    private String reportMode;

    private String startDate;
    private String endDate;

    private String lastExportTime;
    private String currentExportTime;

    private Integer totalTargetCount;
    private Integer totalActualCount;
    private Double overallCompletionRate;

    private List<MedicationSummaryDTO> medicationSummaries;
    private List<DailySummaryDTO> dailySummaries;
    private List<DailyMedicationDTO> dailyBreakdown;
}