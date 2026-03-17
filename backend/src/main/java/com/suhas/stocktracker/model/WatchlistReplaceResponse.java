package com.suhas.stocktracker.model;

import java.util.List;

public record WatchlistReplaceResponse(
    boolean ok,
    String group,
    int count,
    List<String> guessedSymbols,
    String message
) {
}
