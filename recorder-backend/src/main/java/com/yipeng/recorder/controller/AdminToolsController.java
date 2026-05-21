package com.yipeng.recorder.controller;

import com.yipeng.recorder.exception.ForbiddenException;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.request.job.CreateScheduledJobConfigRequest;
import com.yipeng.recorder.request.job.UpdateScheduledJobConfigRequest;
import com.yipeng.recorder.response.job.JobExecutionResponse;
import com.yipeng.recorder.response.job.ScheduledJobConfigResponse;
import com.yipeng.recorder.service.AdminJobService;
import com.yipeng.recorder.service.CronService;
import com.yipeng.recorder.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin-tools")
public class AdminToolsController {

    private final UserService userService;
    private final CronService cronService;
    private final AdminJobService adminJobService;

    public AdminToolsController(UserService userService, CronService cronService, AdminJobService adminJobService) {
        this.userService = userService;
        this.cronService = cronService;
        this.adminJobService = adminJobService;
    }

    @PostMapping("/market-news-summary-record")
    public ResponseEntity<Map<String, Object>> createMarketNewsSummaryRecord() {
        User user = userService.findUserFromAuthentication();
        if (user == null || !user.isAdmin()) {
            throw new ForbiddenException();
        }
        cronService.createManualMarketNewsSummaryRecordAsync();
        return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Market news summary job started."
        ));
    }

    @GetMapping("/jobs/configs")
    public ResponseEntity<List<ScheduledJobConfigResponse>> listJobConfigs() {
        requireAdmin();
        List<ScheduledJobConfigResponse> response = adminJobService.listConfigs().stream()
                .map(ScheduledJobConfigResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }

    @GetMapping("/jobs/dashboard")
    public ResponseEntity<Map<String, Object>> getJobDashboard() {
        requireAdmin();
        return ResponseEntity.ok(adminJobService.getDashboard());
    }

    @GetMapping("/jobs/configs/{id}")
    public ResponseEntity<ScheduledJobConfigResponse> getJobConfig(@PathVariable("id") Long id) {
        requireAdmin();
        return ResponseEntity.ok(ScheduledJobConfigResponse.from(adminJobService.getConfig(id)));
    }

    @PostMapping("/jobs/configs")
    public ResponseEntity<ScheduledJobConfigResponse> createJobConfig(@RequestBody CreateScheduledJobConfigRequest request) {
        requireAdmin();
        return ResponseEntity.ok(ScheduledJobConfigResponse.from(adminJobService.createConfig(request)));
    }

    @PutMapping("/jobs/configs/{id}")
    public ResponseEntity<ScheduledJobConfigResponse> updateJobConfig(@PathVariable("id") Long id,
                                                                      @RequestBody UpdateScheduledJobConfigRequest request) {
        requireAdmin();
        return ResponseEntity.ok(ScheduledJobConfigResponse.from(adminJobService.updateConfig(id, request)));
    }

    @DeleteMapping("/jobs/configs/{id}")
    public ResponseEntity<Map<String, Object>> deleteJobConfig(@PathVariable("id") Long id) {
        requireAdmin();
        adminJobService.deleteConfig(id);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/jobs/configs/{id}/trigger")
    public ResponseEntity<JobExecutionResponse> triggerJob(@PathVariable("id") Long id) {
        User admin = requireAdmin();
        return ResponseEntity.accepted().body(JobExecutionResponse.from(adminJobService.triggerJob(id, admin)));
    }

    @GetMapping("/jobs/executions")
    public ResponseEntity<Page<JobExecutionResponse>> listJobExecutions(@RequestParam(name = "page", defaultValue = "0") int page,
                                                                       @RequestParam(name = "size", defaultValue = "20") int size) {
        requireAdmin();
        Page<JobExecutionResponse> response = adminJobService.listExecutions(PageRequest.of(page, size))
                .map(JobExecutionResponse::from);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/jobs/executions/{id}")
    public ResponseEntity<JobExecutionResponse> getJobExecution(@PathVariable("id") Long id) {
        requireAdmin();
        return ResponseEntity.ok(JobExecutionResponse.from(adminJobService.getExecution(id)));
    }

    private User requireAdmin() {
        User user = userService.findUserFromAuthentication();
        if (user == null || !user.isAdmin()) {
            throw new ForbiddenException();
        }
        return user;
    }
}
