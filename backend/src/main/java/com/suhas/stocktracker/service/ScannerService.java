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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
        List<WatchlistStock> stocks = watchlistService.getWatchlistForStrategy(strategyType);
        Map<String, List<WatchlistStock>> stocksByYahooSymbol = new LinkedHashMap<>();

        for (WatchlistStock stock : stocks) {
            stocksByYahooSymbol.computeIfAbsent(stock.yahooSymbol(), ignored -> new ArrayList<>()).add(stock);
        }

        for (Map.Entry<String, List<WatchlistStock>> entry : stocksByYahooSymbol.entrySet()) {
            String yahooSymbol = entry.getKey();
            List<WatchlistStock> matchingStocks = entry.getValue();
            try {
                List<Candle> candles = fetchCandles(yahooSymbol);
                for (WatchlistStock stock : matchingStocks) {
                    results.add(evaluate(strategyType, stock, candles));
                }
            } catch (Exception exception) {
                String message = exception.getMessage();
                for (WatchlistStock stock : matchingStocks) {
                    failed.add(stock.symbol() + ": " + message);
                }
            }

            try {
                Thread.sleep(appProperties.scanner().pauseMillis());
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                for (WatchlistStock stock : matchingStocks) {
                    failed.add(stock.symbol() + ": interrupted");
                }
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
        Double sequenceEntryLow = null;
        Double sequenceHigh = null;
        boolean sequenceStarted = false;
        Double firstGreenCandleSma200 = null;
        Double latestFormationMove = null;
        Double latestFormationLow = null;
        Double latestFormationHigh = null;
        String latestFormationStartDate = null;
        String latestFormationEndDate = null;
        String currentSequenceStartDate = null;
        int latestFormationEndIndex = -1;
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
                    sequenceEntryLow = candle.low();
                    sequenceHigh = candle.high();
                    sequenceStarted = true;
                    currentSequenceStartDate = candle.time();
                    firstGreenCandleSma200 = sma(closes, 200);
                } else {
                    sequenceHigh = Math.max(sequenceHigh, candle.high());
                }
            } else {
                sequenceEntryLow = null;
                sequenceHigh = null;
                sequenceStarted = false;
                currentSequenceStartDate = null;
                firstGreenCandleSma200 = null;
            }

            if (sequenceStarted && sequenceEntryLow != null && sequenceHigh != null) {
                double percentageMove = ((sequenceHigh - sequenceEntryLow) / sequenceEntryLow) * 100.0;
                boolean conditionMet = percentageMove >= minPercentageMove;
                if (conditionMet) {
                    boolean passesV200StartRule = !"V200".equalsIgnoreCase(stock.group())
                        || (firstGreenCandleSma200 != null && sequenceEntryLow < firstGreenCandleSma200);
                    if (passesV200StartRule) {
                        latestFormationMove = percentageMove;
                        latestFormationLow = sequenceEntryLow;
                        latestFormationHigh = sequenceHigh;
                        latestFormationStartDate = currentSequenceStartDate;
                        latestFormationEndDate = candle.time();
                        latestFormationEndIndex = candleIndex;
                    }
                    sequenceEntryLow = null;
                    sequenceHigh = null;
                    sequenceStarted = false;
                    currentSequenceStartDate = null;
                    firstGreenCandleSma200 = null;
                }
            }
        }

        Candle latest = candles.getLast();
        boolean entryTriggered = false;
        boolean exitTriggered = false;
        String entryTriggerDate = null;
        String exitTriggerDate = null;

        if (latestFormationLow != null && latestFormationHigh != null && latestFormationEndIndex >= 0) {
            for (int index = latestFormationEndIndex + 1; index < candles.size(); index++) {
                Candle candle = candles.get(index);
                if (!entryTriggered && candle.low() <= latestFormationLow) {
                    entryTriggered = true;
                    entryTriggerDate = candle.time();
                }

                if (entryTriggered && candle.high() >= latestFormationHigh) {
                    exitTriggered = true;
                    exitTriggerDate = candle.time();
                    break;
                }
            }
        }

        String signal = "NONE";
        String signalDate = latestFormationEndDate;
        if (entryTriggered && !exitTriggered) {
            signal = "BUY";
            signalDate = entryTriggerDate;
        } else if (entryTriggered) {
            signal = "SELL";
            signalDate = exitTriggerDate;
        }

        String notes;
        if (latestFormationLow == null || latestFormationHigh == null) {
            notes = "No V20 sequence hit the threshold on the available history.";
        } else if (!entryTriggered) {
            notes = "Latest V20 setup exists, but price has never revisited the entry level after the formation.";
        } else if (!exitTriggered) {
            notes = "Latest V20 setup is active because price revisited the entry level and has not yet reached the target.";
        } else {
            notes = "Latest V20 setup completed because price revisited the entry level first and later reached the target.";
        }

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
            signal,
            StrategyType.V20.displayName(),
            signalDate,
            latest.close(),
            null,
            null,
            sma200,
            latestFormationMove,
            latestFormationLow,
            latestFormationHigh,
            latestFormationStartDate,
            latestFormationEndDate,
            percentBelowLifetimeHigh,
            high52Week,
            low52Week,
            false,
            false,
            OffsetDateTime.now().toString(),
            notes
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
