package com.caregiver.controller;

import com.caregiver.dto.DashboardResponse;
import com.caregiver.dto.TaskResponse;
import com.caregiver.service.DashboardService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@CrossOrigin(origins = "*")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/{caregiverId}")
    public DashboardResponse getDashboard(@PathVariable Long caregiverId) {
        return dashboardService.getDashboardSummary(caregiverId);
    }

    @GetMapping("/{caregiverId}/today")
    public List<TaskResponse> getTodayTasks(@PathVariable Long caregiverId) {
        return dashboardService.getTodayTasks(caregiverId);
    }

    @GetMapping("/{caregiverId}/pending")
    public List<TaskResponse> getPendingReminders(@PathVariable Long caregiverId) {
        return dashboardService.getPendingReminders(caregiverId);
    }

    @GetMapping("/{caregiverId}/overdue")
    public List<TaskResponse> getOverdueTasks(@PathVariable Long caregiverId) {
        return dashboardService.getOverdueTasks(caregiverId);
    }

    @GetMapping("/{caregiverId}/upcoming")
    public List<TaskResponse> getUpcomingTasks(@PathVariable Long caregiverId) {
        return dashboardService.getUpcomingTasks(caregiverId);
    }
}