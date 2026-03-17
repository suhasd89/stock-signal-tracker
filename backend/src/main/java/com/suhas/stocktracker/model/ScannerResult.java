package com.suhas.stocktracker.model;

public record ScannerResult(
    String strategySlug,
    String ticker,
    String yahooSymbol,
    String signal,
    String strategy,
    String signalDate,
    Double lastClose,
    Double sma20,
    Double sma50,
    Double sma200,
    Double percentMove,
    Double entryPrice,
    Double targetPrice,
    String sequenceStartDate,
    String sequenceEndDate,
    Double percentBelowLifetimeHigh,
    Double high52Week,
    Double low52Week,
    boolean buyRegion,
    boolean sellRegion,
    String scannedAt,
    String notes
) {
}
