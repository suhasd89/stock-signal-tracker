package com.suhas.stocktracker.model;

public record WatchlistReplaceRequest(
    String group,
    String rawText
) {
}
