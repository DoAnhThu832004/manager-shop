package com.thupo.managerShop.auth.controller;

import java.time.Instant;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller kiểm tra trạng thái hoạt động của server (Health Check).
 * Phục vụ cho Render Health Check và UptimeRobot/Cron-job ping giữ server không bị sleep (trả về 200 OK).
 */
@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    @GetMapping
    public ResponseEntity<Map<String, Object>> checkHealth() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "message", "Service is healthy and running",
                "timestamp", Instant.now().toString()
        ));
    }
}
