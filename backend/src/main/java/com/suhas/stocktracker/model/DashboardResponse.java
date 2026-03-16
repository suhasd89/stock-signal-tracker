package com.suhas.stocktracker.model;

import java.util.List;
import java.util.Map;

public record DashboardResponse(
    String strategy,
    List<WatchlistRow> watchlist,
    List<AlertRecord> recentAlerts,
    Map<String, Integer> summary,
    ScannerSummary scanner,
    List<AlertRecord> orphanAlerts,
    String serverTime
) {

    public record ScannerSummary(ScannerRun lastRun, int coverage) {
    }
}
