package com.suhas.stocktracker.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suhas.stocktracker.model.StrategyType;
import com.suhas.stocktracker.model.WatchlistStock;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class WatchlistService {
    private final List<WatchlistStock> watchlist;

    public WatchlistService(ObjectMapper objectMapper) throws IOException {
        try (InputStream inputStream = new ClassPathResource("watchlist.json").getInputStream()) {
            this.watchlist = objectMapper.readValue(inputStream, new TypeReference<>() {});
        }
    }

    public List<WatchlistStock> getWatchlist() {
        return watchlist;
    }

    public List<WatchlistStock> getWatchlistForStrategy(StrategyType strategyType) {
        return watchlist.stream()
            .filter(stock -> isEligibleForStrategy(stock, strategyType))
            .toList();
    }

    private boolean isEligibleForStrategy(WatchlistStock stock, StrategyType strategyType) {
        if (strategyType == StrategyType.SMA) {
            return !"V40 NEXT".equalsIgnoreCase(stock.group());
        }
        return true;
    }
}
