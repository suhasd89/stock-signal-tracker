package com.suhas.stocktracker.service;

import com.suhas.stocktracker.config.AppProperties;
import com.suhas.stocktracker.model.AlertPayload;
import org.springframework.stereotype.Service;

@Service
public class AlertService {
    private final DatabaseService databaseService;
    private final AppProperties appProperties;

    public AlertService(DatabaseService databaseService, AppProperties appProperties) {
        this.databaseService = databaseService;
        this.appProperties = appProperties;
    }

    public long accept(AlertPayload payload) {
        if (payload.ticker() == null || payload.ticker().isBlank()) {
            throw new IllegalArgumentException("ticker is required");
        }
        if (payload.action() == null || payload.action().isBlank()) {
            throw new IllegalArgumentException("action is required");
        }
        if (!appProperties.webhookSecret().equals(payload.secret())) {
            throw new IllegalArgumentException("unauthorized");
        }
        return databaseService.insertAlert(payload);
    }
}
