package com.suhas.stocktracker.model;

public record WatchlistRow(
    String symbol,
    String name,
    String group,
    String yahooSymbol,
    String strategySlug,
    String scannerSignal,
    String scannerStrategy,
    Double scannerPrice,
    String scannerSignalDate,
    String scannerScannedAt,
    String scannerNotes,
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
    String notes
) {
}
