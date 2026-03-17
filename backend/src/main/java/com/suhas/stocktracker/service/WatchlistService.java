package com.suhas.stocktracker.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.suhas.stocktracker.config.AppProperties;
import com.suhas.stocktracker.model.StrategyType;
import com.suhas.stocktracker.model.WatchlistStock;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class WatchlistService {
    private final ObjectMapper yamlMapper;
    private final AppProperties appProperties;

    public WatchlistService(AppProperties appProperties) {
        this.yamlMapper = new YAMLMapper();
        this.appProperties = appProperties;
    }

    public List<WatchlistStock> getWatchlist() {
        try {
            return loadWatchlist();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load watchlists", exception);
        }
    }

    public List<WatchlistStock> getWatchlistForStrategy(StrategyType strategyType) {
        return getWatchlist().stream()
            .filter(stock -> isEligibleForStrategy(stock, strategyType))
            .toList();
    }

    private boolean isEligibleForStrategy(WatchlistStock stock, StrategyType strategyType) {
        if (strategyType == StrategyType.SMA) {
            return "V40".equalsIgnoreCase(stock.group());
        }
        return true;
    }

    private List<WatchlistStock> loadWatchlist() throws IOException {
        List<WatchlistStock> loaded = new ArrayList<>();
        for (Map.Entry<String, String> entry : appProperties.watchlists().resources().entrySet()) {
            loaded.addAll(readClasspathWatchlist(entry.getValue(), entry.getKey()));
        }
        return loaded;
    }

    private List<WatchlistStock> readClasspathWatchlist(String resourceName, String group) throws IOException {
        try (InputStream inputStream = new ClassPathResource(resourceName).getInputStream()) {
            return readWatchlist(inputStream, group);
        }
    }

    private List<WatchlistStock> readWatchlist(InputStream inputStream, String group) throws IOException {
        WatchlistFile watchlistFile = yamlMapper.readValue(inputStream, WatchlistFile.class);
        return watchlistFile.stocks().stream()
            .map(stock -> new WatchlistStock(stock.symbol(), stock.name(), group, stock.yahooSymbol()))
            .toList();
    }

    private record WatchlistFile(List<WatchlistEntry> stocks) {
    }

    private record WatchlistEntry(String symbol, String name, String yahooSymbol) {
    }
}
