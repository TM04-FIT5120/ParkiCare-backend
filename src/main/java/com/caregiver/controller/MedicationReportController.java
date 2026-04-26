package com.caregiver.controller;

import com.caregiver.dto.MedicationReportDTO;
import com.caregiver.service.MedicationReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class MedicationReportController {

    private final MedicationReportService medicationReportService;

    @GetMapping("/{patientId}")
    public MedicationReportDTO getMedicationReport(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "custom") String mode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        LocalDate start = startDate == null ? null : LocalDate.parse(startDate);
        LocalDate end = endDate == null ? null : LocalDate.parse(endDate);

        return medicationReportService.generateReport(
                patientId,
                start,
                end,
                mode
        );
    }

    @GetMapping("/{patientId}/pdf")
    public ResponseEntity<byte[]> exportMedicationReportPdf(
            @PathVariable Long patientId,
            @RequestParam(defaultValue = "custom") String mode,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        LocalDate start = startDate == null ? null : LocalDate.parse(startDate);
        LocalDate end = endDate == null ? null : LocalDate.parse(endDate);

        byte[] pdfBytes = medicationReportService.exportMedicationReportPdf(
                patientId,
                start,
                end,
                mode
        );

        String fileName = "medication_report_" + patientId + "_" + mode + ".pdf";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + fileName)
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdfBytes);
    }
}