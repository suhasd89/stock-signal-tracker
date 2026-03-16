package com.suhas.stocktracker.model;

public record AlertRecord(
    long id,
    String ticker,
    String action,
    String strategy,
    String exchange,
    Double price,
    String timeframe,
    String notes,
    String source,
    String eventTime,
    String createdAt
) {
}
