package com.suhas.stocktracker.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(String webhookSecret, Scanner scanner) {

    public record Scanner(String range, String interval, String strategyName, long pauseMillis) {
    }
}
