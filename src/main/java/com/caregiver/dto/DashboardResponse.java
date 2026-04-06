package com.caregiver.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class DashboardResponse {

    private List<TaskResponse> todayTasks;
    private List<TaskResponse> pendingReminders;
    private List<TaskResponse> overdueTasks;
    private List<TaskResponse> upcomingTasks;
    private boolean empty;
}