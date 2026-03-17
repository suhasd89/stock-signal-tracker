package com.suhas.stocktracker.model;

import java.util.List;

public record WatchlistAdminResponse(
    List<WatchlistGroupSummary> groups,
    List<WatchlistStock> stocks
) {
}
