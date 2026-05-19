package com.yipeng.recorder.controller;

import com.yipeng.recorder.exception.ForbiddenException;
import com.yipeng.recorder.model.User;
import com.yipeng.recorder.service.CronService;
import com.yipeng.recorder.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin-tools")
public class AdminToolsController {

    private final UserService userService;
    private final CronService cronService;

    public AdminToolsController(UserService userService, CronService cronService) {
        this.userService = userService;
        this.cronService = cronService;
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
}
