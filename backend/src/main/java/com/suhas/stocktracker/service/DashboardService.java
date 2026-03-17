package com.suhas.stocktracker.service;

import com.suhas.stocktracker.model.DashboardResponse;
import com.suhas.stocktracker.model.ScannerResult;
import com.suhas.stocktracker.model.ScannerRun;
import com.suhas.stocktracker.model.StrategyType;
import com.suhas.stocktracker.model.WatchlistRow;
import com.suhas.stocktracker.model.WatchlistStock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {
    private final WatchlistService watchlistService;
    private final DatabaseService databaseService;

    public DashboardService(WatchlistService watchlistService, DatabaseService databaseService) {
        this.watchlistService = watchlistService;
        this.databaseService = databaseService;
    }

    public DashboardResponse fetchDashboard(StrategyType strategyType) {
        List<WatchlistStock> watchlist = watchlistService.getWatchlistForStrategy(strategyType);
        Map<String, ScannerResult> scansByTicker = databaseService.fetchScannerResults(strategyType)
            .stream()
            .collect(Collectors.toMap(ScannerResult::ticker, Function.identity(), (left, right) -> left));
        ScannerRun latestRun = databaseService.fetchLatestScannerRun(strategyType);

        List<WatchlistRow> rows = watchlist.stream().map(stock -> {
            ScannerResult scan = scansByTicker.get(stock.symbol());
            return new WatchlistRow(
                stock.symbol(),
                stock.name(),
                stock.group(),
                stock.yahooSymbol(),
                strategyType.slug(),
                scan != null ? scan.signal() : "NONE",
                scan != null ? scan.strategy() : strategyType.displayName(),
                scan != null ? scan.lastClose() : null,
                scan != null ? scan.signalDate() : null,
                scan != null ? scan.scannedAt() : null,
                scan != null ? scan.notes() : "",
                scan != null ? scan.sma20() : null,
                scan != null ? scan.sma50() : null,
                scan != null ? scan.sma200() : null,
                scan != null ? scan.percentMove() : null,
                scan != null ? scan.entryPrice() : null,
                scan != null ? scan.targetPrice() : null,
                scan != null ? scan.sequenceStartDate() : null,
                scan != null ? scan.sequenceEndDate() : null,
                scan != null ? scan.percentBelowLifetimeHigh() : null,
                scan != null ? scan.high52Week() : null,
                scan != null ? scan.low52Week() : null,
                scan != null ? scan.notes() : ""
            );
        }).toList();

        int buySignals = (int) rows.stream().filter(row -> "BUY".equals(row.scannerSignal()) || "ALERT".equals(row.scannerSignal())).count();
        int sellSignals = (int) rows.stream().filter(row -> "SELL".equals(row.scannerSignal())).count();

        return new DashboardResponse(
            strategyType.slug(),
            rows,
            Map.of(
                "trackedStocks", watchlist.size(),
                "activeSignals", buySignals + sellSignals,
                "buySignals", buySignals,
                "sellSignals", sellSignals
            ),
            new DashboardResponse.ScannerSummary(latestRun, scansByTicker.size()),
            OffsetDateTime.now().toString()
        );
    }
}
