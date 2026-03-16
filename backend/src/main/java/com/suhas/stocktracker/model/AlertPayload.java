package com.suhas.stocktracker.model;

import com.fasterxml.jackson.annotation.JsonAlias;

public record AlertPayload(
    String secret,
    @JsonAlias({"symbol"}) String ticker,
    String exchange,
    String action,
    @JsonAlias({"strategy_name"}) String strategy,
    Double price,
    String timeframe,
    @JsonAlias({"timestamp"}) String eventTime,
    String notes,
    String source
) {
}
