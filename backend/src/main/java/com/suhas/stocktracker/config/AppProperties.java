package com.suhas.stocktracker.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record AppProperties(Scanner scanner, Watchlists watchlists) {

    public record Scanner(String range, String interval, String strategyName, long pauseMillis) {
    }

    public record Watchlists(Map<String, String> resources) {
    }
}
