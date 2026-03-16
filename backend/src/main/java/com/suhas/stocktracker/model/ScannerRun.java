package com.suhas.stocktracker.model;

public record ScannerRun(
    long id,
    String strategySlug,
    String status,
    String message,
    String startedAt,
    String completedAt,
    int stocksScanned
) {
}
