package com.caregiver.service;

import com.caregiver.dto.*;
import com.caregiver.entity.DrugBase;
import com.caregiver.entity.MedicationPlan;
import com.caregiver.repository.DrugBaseRepository;
import com.caregiver.repository.MedicationPlanRepository;
import com.caregiver.util.ExportTimeFileStore;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MedicationReportService {

    private final MedicationPlanRepository medicationPlanRepository;
    private final DrugBaseRepository drugBaseRepository;

    public MedicationReportDTO generateReport(
            Long patientId,
            LocalDate startDate,
            LocalDate endDate,
            String mode
    ) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime lastExportTime = ExportTimeFileStore.get(patientId);

        String reportMode = mode == null ? "custom" : mode;

        LocalDate actualStartDate;
        LocalDate actualEndDate;

        if ("sinceLastExport".equalsIgnoreCase(reportMode)) {

            if (lastExportTime == null) {
                lastExportTime = now.minusDays(7);
                ExportTimeFileStore.save(patientId, lastExportTime);
            }

            actualStartDate = lastExportTime.toLocalDate();
            actualEndDate = now.toLocalDate();

        } else {
            if (startDate == null || endDate == null) {
                throw new RuntimeException("startDate and endDate are required for custom mode");
            }

            actualStartDate = startDate;
            actualEndDate = endDate;
        }

        List<MedicationPlan> plans =
                medicationPlanRepository.findByPatientIdAndStartDateBetweenAndIsValid(
                        patientId,
                        actualStartDate,
                        actualEndDate,
                        1
                );

        plans.sort(
                Comparator.comparing(MedicationPlan::getStartDate)
                        .thenComparing(MedicationPlan::getDrugId)
                        .thenComparing(MedicationPlan::getAdminTime)
        );

        MedicationReportDTO report = new MedicationReportDTO();

        report.setPatientId(patientId);
        report.setReportMode(reportMode);
        report.setStartDate(actualStartDate.toString());
        report.setEndDate(actualEndDate.toString());
        report.setCurrentExportTime(now.toString());
        report.setLastExportTime(lastExportTime == null ? null : lastExportTime.toString());

        if (plans.isEmpty()) {
            report.setTotalTargetCount(0);
            report.setTotalActualCount(0);
            report.setOverallCompletionRate(0.0);
            report.setMedicationSummaries(new ArrayList<>());
            report.setDailySummaries(new ArrayList<>());
            report.setDailyBreakdown(new ArrayList<>());
            return report;
        }

        List<Long> drugIds = plans.stream()
                .map(MedicationPlan::getDrugId)
                .distinct()
                .toList();

        Map<Long, String> drugNameMap = drugBaseRepository.findByDrugIdIn(drugIds)
                .stream()
                .collect(Collectors.toMap(
                        DrugBase::getDrugId,
                        DrugBase::getDrugName
                ));

        Map<LocalDate, Map<Long, List<MedicationPlan>>> dateDrugMap =
                plans.stream().collect(Collectors.groupingBy(
                        MedicationPlan::getStartDate,
                        TreeMap::new,
                        Collectors.groupingBy(MedicationPlan::getDrugId)
                ));

        List<DailyMedicationDTO> dailyBreakdown = new ArrayList<>();
        List<DailySummaryDTO> dailySummaries = new ArrayList<>();

        int totalTarget = 0;
        int totalActual = 0;

        for (Map.Entry<LocalDate, Map<Long, List<MedicationPlan>>> dateEntry : dateDrugMap.entrySet()) {

            LocalDate date = dateEntry.getKey();

            int dailyTarget = 0;
            int dailyActual = 0;

            Map<Long, List<MedicationPlan>> drugMap = dateEntry.getValue();

            List<Long> sortedDrugIds = new ArrayList<>(drugMap.keySet());
            sortedDrugIds.sort(Comparator.naturalOrder());

            for (Long drugId : sortedDrugIds) {

                List<MedicationPlan> drugPlans = drugMap.get(drugId);
                drugPlans.sort(Comparator.comparing(MedicationPlan::getAdminTime));

                int targetFrequency = drugPlans.size();

                int actualCount = (int) drugPlans.stream()
                        .filter(p -> p.getRemindStatus() != null && p.getRemindStatus() == 1)
                        .count();

                double completionRate = calculateRate(actualCount, targetFrequency);
                String status = actualCount == targetFrequency ? "SUCCESS" : "INCOMPLETE";

                dailyBreakdown.add(new DailyMedicationDTO(
                        date.toString(),
                        drugId,
                        drugNameMap.getOrDefault(drugId, "Unknown Drug"),
                        targetFrequency,
                        actualCount,
                        completionRate,
                        status
                ));

                dailyTarget += targetFrequency;
                dailyActual += actualCount;
            }

            double dailyRate = calculateRate(dailyActual, dailyTarget);
            String dailyStatus = dailyActual == dailyTarget ? "SUCCESS" : "INCOMPLETE";

            dailySummaries.add(new DailySummaryDTO(
                    date.toString(),
                    dailyTarget,
                    dailyActual,
                    dailyRate,
                    dailyStatus
            ));

            totalTarget += dailyTarget;
            totalActual += dailyActual;
        }

        Map<Long, List<DailyMedicationDTO>> drugDailyMap =
                dailyBreakdown.stream()
                        .collect(Collectors.groupingBy(DailyMedicationDTO::getDrugId));

        List<MedicationSummaryDTO> medicationSummaries = new ArrayList<>();

        for (Map.Entry<Long, List<DailyMedicationDTO>> entry : drugDailyMap.entrySet()) {

            Long drugId = entry.getKey();
            List<DailyMedicationDTO> records = entry.getValue();

            int targetCount = records.stream()
                    .mapToInt(DailyMedicationDTO::getTargetFrequency)
                    .sum();

            int actualCount = records.stream()
                    .mapToInt(DailyMedicationDTO::getActualCount)
                    .sum();

            int successDays = (int) records.stream()
                    .filter(r -> "SUCCESS".equals(r.getStatus()))
                    .count();

            int incompleteDays = (int) records.stream()
                    .filter(r -> "INCOMPLETE".equals(r.getStatus()))
                    .count();

            medicationSummaries.add(new MedicationSummaryDTO(
                    drugId,
                    drugNameMap.getOrDefault(drugId, "Unknown Drug"),
                    targetCount,
                    actualCount,
                    calculateRate(actualCount, targetCount),
                    successDays,
                    incompleteDays
            ));
        }

        medicationSummaries.sort(Comparator.comparing(MedicationSummaryDTO::getDrugName));

        report.setTotalTargetCount(totalTarget);
        report.setTotalActualCount(totalActual);
        report.setOverallCompletionRate(calculateRate(totalActual, totalTarget));
        report.setMedicationSummaries(medicationSummaries);
        report.setDailySummaries(dailySummaries);
        report.setDailyBreakdown(dailyBreakdown);

        return report;
    }

    public byte[] exportMedicationReportPdf(
            Long patientId,
            LocalDate startDate,
            LocalDate endDate,
            String mode
    ) {
        MedicationReportDTO report = generateReport(patientId, startDate, endDate, mode);

        byte[] pdfBytes = generatePdf(report);

        ExportTimeFileStore.save(patientId, LocalDateTime.now());

        return pdfBytes;
    }

    private byte[] generatePdf(MedicationReportDTO report) {

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            Document document = new Document(PageSize.A4.rotate());
            PdfWriter.getInstance(document, outputStream);

            document.open();

            Font titleFont = new Font(Font.HELVETICA, 18, Font.BOLD);
            Font sectionFont = new Font(Font.HELVETICA, 13, Font.BOLD);
            Font normalFont = new Font(Font.HELVETICA, 10);
            Font tableFont = new Font(Font.HELVETICA, 9);

            Paragraph title = new Paragraph("Medication Adherence Report", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            document.add(new Paragraph(" "));

            document.add(new Paragraph("Patient ID: " + report.getPatientId(), normalFont));
            document.add(new Paragraph("Report Mode: " + report.getReportMode(), normalFont));
            document.add(new Paragraph("Date Range: " + report.getStartDate() + " to " + report.getEndDate(), normalFont));
            document.add(new Paragraph("Current Export Time: " + report.getCurrentExportTime(), normalFont));
            document.add(new Paragraph(
                    "Last Export Time: " +
                            (report.getLastExportTime() == null ? "No previous export" : report.getLastExportTime()),
                    normalFont
            ));

            document.add(new Paragraph(" "));

            document.add(new Paragraph("Executive Summary", sectionFont));
            document.add(new Paragraph("Total Target Count: " + report.getTotalTargetCount(), normalFont));
            document.add(new Paragraph("Total Actual Count: " + report.getTotalActualCount(), normalFont));
            document.add(new Paragraph(
                    "Overall Completion Rate: " + formatRate(report.getOverallCompletionRate()) + "%",
                    normalFont
            ));

            document.add(new Paragraph(" "));

            document.add(new Paragraph("Medication Summary", sectionFont));

            // Removed "Drug ID" column (was 7 columns); keep only human-readable drug name and metrics.
            PdfPTable medicationTable = new PdfPTable(6);
            medicationTable.setWidthPercentage(100);

            addTableHeader(
                    medicationTable,
                    "Drug Name",
                    "Target",
                    "Actual",
                    "Rate",
                    "Success Days",
                    "Incomplete Days"
            );

            for (MedicationSummaryDTO item : report.getMedicationSummaries()) {
                medicationTable.addCell(new Phrase(item.getDrugName(), tableFont));
                medicationTable.addCell(new Phrase(String.valueOf(item.getTargetCount()), tableFont));
                medicationTable.addCell(new Phrase(String.valueOf(item.getActualCount()), tableFont));
                medicationTable.addCell(new Phrase(formatRate(item.getCompletionRate()) + "%", tableFont));
                medicationTable.addCell(new Phrase(String.valueOf(item.getSuccessDays()), tableFont));
                medicationTable.addCell(new Phrase(String.valueOf(item.getIncompleteDays()), tableFont));
            }

            document.add(medicationTable);

            document.add(new Paragraph(" "));

            document.add(new Paragraph("Daily Summary", sectionFont));

            PdfPTable dailySummaryTable = new PdfPTable(5);
            dailySummaryTable.setWidthPercentage(100);

            addTableHeader(
                    dailySummaryTable,
                    "Date",
                    "Target Count",
                    "Actual Count",
                    "Completion Rate",
                    "Status"
            );

            for (DailySummaryDTO item : report.getDailySummaries()) {
                dailySummaryTable.addCell(new Phrase(item.getDate(), tableFont));
                dailySummaryTable.addCell(new Phrase(String.valueOf(item.getTargetCount()), tableFont));
                dailySummaryTable.addCell(new Phrase(String.valueOf(item.getActualCount()), tableFont));
                dailySummaryTable.addCell(new Phrase(formatRate(item.getCompletionRate()) + "%", tableFont));
                dailySummaryTable.addCell(new Phrase(item.getStatus(), tableFont));
            }

            document.add(dailySummaryTable);

            document.add(new Paragraph(" "));

            document.add(new Paragraph("Daily Medication Breakdown", sectionFont));

            // Removed "Drug ID" column (was 7 columns); keep date + drug name + metrics.
            PdfPTable breakdownTable = new PdfPTable(6);
            breakdownTable.setWidthPercentage(100);

            addTableHeader(
                    breakdownTable,
                    "Date",
                    "Drug Name",
                    "Target Frequency",
                    "Actual Count",
                    "Rate",
                    "Status"
            );

            for (DailyMedicationDTO item : report.getDailyBreakdown()) {
                breakdownTable.addCell(new Phrase(item.getDate(), tableFont));
                breakdownTable.addCell(new Phrase(item.getDrugName(), tableFont));
                breakdownTable.addCell(new Phrase(String.valueOf(item.getTargetFrequency()), tableFont));
                breakdownTable.addCell(new Phrase(String.valueOf(item.getActualCount()), tableFont));
                breakdownTable.addCell(new Phrase(formatRate(item.getCompletionRate()) + "%", tableFont));
                breakdownTable.addCell(new Phrase(item.getStatus(), tableFont));
            }

            document.add(breakdownTable);

            // --- Medication Plan Details (per your requirement) ---
            // Show all medication_plan records that match the same patient + date range used by the report statistics.
            document.add(new Paragraph(" "));
            document.add(new Paragraph("Medication Plan Details", sectionFont));

            LocalDate pdfStartDate = LocalDate.parse(report.getStartDate());
            LocalDate pdfEndDate = LocalDate.parse(report.getEndDate());

            List<MedicationPlan> detailPlans = medicationPlanRepository
                    .findByPatientIdAndStartDateBetweenAndIsValid(
                            report.getPatientId(),
                            pdfStartDate,
                            pdfEndDate,
                            1
                    );

            detailPlans.sort(
                    Comparator.comparing(MedicationPlan::getStartDate)
                            .thenComparing(MedicationPlan::getAdminTime)
                            .thenComparing(MedicationPlan::getDrugId)
            );

            List<Long> detailDrugIds = detailPlans.stream()
                    .map(MedicationPlan::getDrugId)
                    .distinct()
                    .toList();

            Map<Long, DrugBase> detailDrugMap = drugBaseRepository
                    .findByDrugIdIn(detailDrugIds)
                    .stream()
                    .collect(Collectors.toMap(DrugBase::getDrugId, d -> d));

            PdfPTable detailTable = new PdfPTable(9);
            detailTable.setWidthPercentage(100);

            addTableHeader(
                    detailTable,
                    "Drug Name",
                    "Drug Company",
                    "Dosage",
                    "Frequency",
                    "Quantity",
                    "Administered time",
                    "Meal timing",
                    "Remind Status",
                    "Notes"
            );

            for (MedicationPlan p : detailPlans) {
                DrugBase drug = detailDrugMap.get(p.getDrugId());

                String drugName = drug == null ? "" : nullSafeToString(drug.getDrugName());
                String drugCompany = drug == null ? "" : nullSafeToString(drug.getManufacturerName());
                String dosage = p.getDosage() == null ? "" : p.getDosage();
                String frequency = p.getFrequency() == null ? "" : p.getFrequency();
                String quantity = p.getQuantity() == null ? "" : String.valueOf(p.getQuantity());
                String adminTime = p.getAdminTime() == null ? "" : p.getAdminTime().toString();
                String mealTiming = p.getMealTiming() == null ? "" : p.getMealTiming();
                String remindStatus = (p.getRemindStatus() != null && p.getRemindStatus() == 1)
                        ? "complete"
                        : "incomplete";
                String notes = p.getPlanNote() == null ? "" : p.getPlanNote();

                detailTable.addCell(new Phrase(drugName, tableFont));
                detailTable.addCell(new Phrase(drugCompany, tableFont));
                detailTable.addCell(new Phrase(dosage, tableFont));
                detailTable.addCell(new Phrase(frequency, tableFont));
                detailTable.addCell(new Phrase(quantity, tableFont));
                detailTable.addCell(new Phrase(adminTime, tableFont));
                detailTable.addCell(new Phrase(mealTiming, tableFont));
                detailTable.addCell(new Phrase(remindStatus, tableFont));
                detailTable.addCell(new Phrase(notes, tableFont));
            }

            document.add(detailTable);

            document.close();

            return outputStream.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to generate medication report PDF", e);
        }
    }

    private void addTableHeader(PdfPTable table, String... headers) {
        Font headerFont = new Font(Font.HELVETICA, 9, Font.BOLD);

        for (String header : headers) {
            table.addCell(new Phrase(header, headerFont));
        }
    }

    private double calculateRate(int actual, int target) {
        if (target == 0) {
            return 0.0;
        }

        return Math.round((actual * 100.0 / target) * 10.0) / 10.0;
    }

    private String formatRate(Double rate) {
        if (rate == null) {
            return "0.0";
        }

        return String.format("%.1f", rate);
    }

    private String nullSafeToString(String value) {
        return value == null ? "" : value;
    }
}