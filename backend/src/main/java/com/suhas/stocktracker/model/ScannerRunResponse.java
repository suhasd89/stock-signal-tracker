package com.suhas.stocktracker.model;

import java.util.List;

public record ScannerRunResponse(
    boolean ok,
    String strategy,
    long runId,
    int scanned,
    List<String> failed,
    String message
) {
}
