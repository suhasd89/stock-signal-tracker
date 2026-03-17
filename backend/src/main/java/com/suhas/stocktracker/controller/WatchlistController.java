package com.suhas.stocktracker.controller;

import com.suhas.stocktracker.model.WatchlistAdminResponse;
import com.suhas.stocktracker.model.WatchlistReplaceRequest;
import com.suhas.stocktracker.model.WatchlistReplaceResponse;
import com.suhas.stocktracker.service.WatchlistService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/watchlists")
public class WatchlistController {
    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    public WatchlistAdminResponse watchlists() {
        return watchlistService.fetchAdminWatchlists();
    }

    @PostMapping("/replace")
    public WatchlistReplaceResponse replace(@RequestBody WatchlistReplaceRequest request) {
        return watchlistService.replaceGroup(request.group(), request.rawText());
    }
}
