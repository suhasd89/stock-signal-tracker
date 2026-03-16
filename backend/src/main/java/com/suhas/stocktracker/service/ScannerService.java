package com.suhas.stocktracker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.suhas.stocktracker.config.AppProperties;
import com.suhas.stocktracker.model.ScannerResult;
import com.suhas.stocktracker.model.ScannerRunResponse;
import com.suhas.stocktracker.model.StrategyType;
import com.suhas.stocktracker.model.WatchlistStock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class ScannerService {
    private final RestClient restClient;
    private final WatchlistService watchlistService;
    private final DatabaseService databaseService;
    private final AppProperties appProperties;

    public ScannerService(RestClient restClient, WatchlistService watchlistService, DatabaseService databaseService,
                          AppProperties appProperties) {
        this.restClient = restClient;
        this.watchlistService = watchlistService;
        this.databaseService = databaseService;
        this.appProperties = appProperties;
    }

    public ScannerRunResponse runScanner(StrategyType strategyType) {
        long runId = databaseService.startScannerRun(strategyType);
        List<ScannerResult> results = new ArrayList<>();
        List<String> failed = new ArrayList<>();

        for (WatchlistStock stock : watchlistService.getWatchlistForStrategy(strategyType)) {
            try {
                List<Candle> candles = fetchCandles(stock.yahooSymbol());
                results.add(evaluate(strategyType, stock, candles));
            } catch (Exception exception) {
                failed.add(stock.symbol() + ": " + exception.getMessage());
            }

            try {
                Thread.sleep(appProperties.scanner().pauseMillis());
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                failed.add(stock.symbol() + ": interrupted");
            }
        }

        databaseService.upsertScannerResults(results);
        String message = failed.isEmpty()
            ? "Scanned " + results.size() + " stocks successfully."
            : "Scanned " + results.size() + " stocks. Failed: " + failed.size() + ".";
        databaseService.finishScannerRun(runId, failed.isEmpty() ? "SUCCESS" : "PARTIAL_SUCCESS", message, results.size());
        return new ScannerRunResponse(true, strategyType.slug(), runId, results.size(), failed, message);
    }

    private List<Candle> fetchCandles(String yahooSymbol) {
        String url = "https://query1.finance.yahoo.com/v8/finance/chart/{symbol}?range={range}&interval={interval}&includeAdjustedClose=true";
        JsonNode body = restClient.get()
            .uri(url, yahooSymbol, appProperties.scanner().range(), appProperties.scanner().interval())
            .header(HttpHeaders.USER_AGENT, "Mozilla/5.0")
            .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .header(HttpHeaders.ACCEPT_LANGUAGE, "en-US,en;q=0.9")
            .header(HttpHeaders.ORIGIN, "https://finance.yahoo.com")
            .header(HttpHeaders.REFERER, "https://finance.yahoo.com/")
            .retrieve()
            .body(JsonNode.class);

        JsonNode result = body.path("chart").path("result").get(0);
        if (result == null || result.isMissingNode()) {
            throw new IllegalStateException("missing chart result");
        }

        JsonNode timestamps = result.path("timestamp");
        JsonNode quote = result.path("indicators").path("quote").get(0);
        JsonNode opens = quote.path("open");
        JsonNode highs = quote.path("high");
        JsonNode lows = quote.path("low");
        JsonNode closes = quote.path("close");
        List<Candle> candles = new ArrayList<>();
        for (int index = 0; index < timestamps.size(); index++) {
            JsonNode close = closes.get(index);
            JsonNode open = opens.get(index);
            JsonNode high = highs.get(index);
            JsonNode low = lows.get(index);
            if (close == null || close.isNull() || open == null || open.isNull() || high == null || high.isNull() || low == null || low.isNull()) {
                continue;
            }
            candles.add(new Candle(
                isoFromUnix(timestamps.get(index).asLong()),
                open.asDouble(),
                high.asDouble(),
                low.asDouble(),
                close.asDouble()
            ));
        }
        if (candles.isEmpty()) {
            throw new IllegalStateException("no candle data returned");
        }
        return candles;
    }

    private ScannerResult evaluate(StrategyType strategyType, WatchlistStock stock, List<Candle> candles) {
        return switch (strategyType) {
            case SMA -> evaluateSma(stock, candles);
            case V20 -> evaluateV20(stock, candles);
        };
    }

    private ScannerResult evaluateSma(WatchlistStock stock, List<Candle> candles) {
        List<Double> closes = new ArrayList<>();
        for (Candle candle : candles) {
            closes.add(candle.close());
        }
        if (closes.size() < 200) {
            throw new IllegalStateException("not enough history to calculate 200 SMA");
        }

        double lastClose = closes.getLast();
        Double sma20 = sma(closes, 20);
        Double sma50 = sma(closes, 50);
        Double sma200 = sma(closes, 200);
        boolean buyRegion = sma200 > sma50 && sma50 > sma20 && sma20 > lastClose;
        boolean sellRegion = lastClose > sma20 && sma20 > sma50 && sma50 > sma200;
        String signal = buyRegion ? "BUY" : sellRegion ? "SELL" : "NONE";
        String signalDate = signal.equals("NONE") ? null : candles.getLast().time();
        String notes = buyRegion
            ? "Price is in buy region based on local daily candle scan."
            : sellRegion
            ? "Price is in sell region based on local daily candle scan."
            : "No active buy/sell region on the latest daily candle.";

        return new ScannerResult(
            StrategyType.SMA.slug(),
            stock.symbol(),
            stock.yahooSymbol(),
            signal,
            StrategyType.SMA.displayName(),
            signalDate,
            lastClose,
            sma20,
            sma50,
            sma200,
            null,
            null,
            null,
            null,
            buyRegion,
            sellRegion,
            OffsetDateTime.now().toString(),
            notes
        );
    }

    private ScannerResult evaluateV20(WatchlistStock stock, List<Candle> candles) {
        double minPercentageMove = 20.0;
        Double sequenceLow = null;
        Double sequenceHigh = null;
        boolean sequenceStarted = false;
        Double lastTriggeredMove = null;
        String lastTriggeredDate = null;
        double lifetimeHigh = Double.NEGATIVE_INFINITY;
        List<Double> highs = new ArrayList<>();
        List<Double> lows = new ArrayList<>();
        List<Double> closes = new ArrayList<>();

        for (int candleIndex = 0; candleIndex < candles.size(); candleIndex++) {
            Candle candle = candles.get(candleIndex);
            highs.add(candle.high());
            lows.add(candle.low());
            closes.add(candle.close());

            if (candle.high() > lifetimeHigh) {
                lifetimeHigh = candle.high();
            }

            boolean isGreenCandle = candle.close() > candle.open();
            if (isGreenCandle) {
                if (!sequenceStarted) {
                    sequenceLow = candle.low();
                    sequenceHigh = candle.high();
                    sequenceStarted = true;
                } else {
                    sequenceLow = Math.min(sequenceLow, candle.low());
                    sequenceHigh = Math.max(sequenceHigh, candle.high());
                }
            } else {
                sequenceLow = null;
                sequenceHigh = null;
                sequenceStarted = false;
            }

            if (sequenceStarted && sequenceLow != null && sequenceHigh != null) {
                double percentageMove = ((sequenceHigh - sequenceLow) / sequenceLow) * 100.0;
                boolean conditionMet = percentageMove >= minPercentageMove;
                if (conditionMet) {
                    lastTriggeredMove = percentageMove;
                    lastTriggeredDate = candle.time();
                    sequenceLow = null;
                    sequenceHigh = null;
                    sequenceStarted = false;
                }
            }
        }

        Candle latest = candles.getLast();
        double percentBelowLifetimeHigh = lifetimeHigh > 0
            ? ((lifetimeHigh - latest.close()) / lifetimeHigh) * 100.0
            : 0.0;
        Double sma200 = sma(closes, 200);
        Double high52Week = rollingExtreme(highs, 260, true);
        Double low52Week = rollingExtreme(lows, 260, false);

        return new ScannerResult(
            StrategyType.V20.slug(),
            stock.symbol(),
            stock.yahooSymbol(),
            lastTriggeredMove != null ? "BUY" : "NONE",
            StrategyType.V20.displayName(),
            lastTriggeredDate,
            latest.close(),
            null,
            null,
            sma200,
            lastTriggeredMove,
            percentBelowLifetimeHigh,
            high52Week,
            low52Week,
            false,
            false,
            OffsetDateTime.now().toString(),
            lastTriggeredMove != null
                ? "Latest completed V20 20% green-candle sequence was on " + lastTriggeredDate + "."
                : "No V20 sequence hit the threshold on the available history."
        );
    }

    private Double sma(List<Double> closes, int period) {
        if (closes.size() < period) {
            return null;
        }
        double sum = 0;
        for (int index = closes.size() - period; index < closes.size(); index++) {
            sum += closes.get(index);
        }
        return sum / period;
    }

    private String isoFromUnix(long unixSeconds) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(unixSeconds), ZoneOffset.UTC).toString();
    }

    private Double rollingExtreme(List<Double> values, int period, boolean highest) {
        if (values.isEmpty()) {
            return null;
        }
        int start = Math.max(0, values.size() - period);
        double extreme = values.get(start);
        for (int index = start + 1; index < values.size(); index++) {
            extreme = highest ? Math.max(extreme, values.get(index)) : Math.min(extreme, values.get(index));
        }
        return extreme;
    }

    private record Candle(String time, double open, double high, double low, double close) {
    }
}
