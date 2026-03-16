package com.suhas.stocktracker.controller;

import com.suhas.stocktracker.model.AlertPayload;
import com.suhas.stocktracker.service.AlertService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tradingview")
public class WebhookController {
    private final AlertService alertService;

    public WebhookController(AlertService alertService) {
        this.alertService = alertService;
    }

    @PostMapping("/webhook")
    public Map<String, Object> webhook(@RequestBody AlertPayload payload) {
        long alertId = alertService.accept(payload);
        return Map.of("ok", true, "alertId", alertId);
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException exception) {
        HttpStatus status = "unauthorized".equals(exception.getMessage()) ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status)
            .body(Map.of("ok", false, "status", status.value(), "error", exception.getMessage()));
    }
}
