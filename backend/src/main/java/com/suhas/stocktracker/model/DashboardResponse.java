package com.suhas.stocktracker.model;

import java.util.List;
import java.util.Map;

public record DashboardResponse(
    String strategy,
    List<WatchlistRow> watchlist,
    Map<String, Integer> summary,
    ScannerSummary scanner,
    String serverTime
) {

    public record ScannerSummary(ScannerRun lastRun, int coverage) {
    }
}
