package com.suhas.stocktracker.service;

import com.suhas.stocktracker.model.AlertPayload;
import com.suhas.stocktracker.model.AlertRecord;
import com.suhas.stocktracker.model.ScannerResult;
import com.suhas.stocktracker.model.ScannerRun;
import com.suhas.stocktracker.model.StrategyType;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class DatabaseService {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    void initialize() {
        ensureDataDirectory();
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS alerts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                ticker TEXT NOT NULL,
                action TEXT NOT NULL,
                strategy TEXT NOT NULL,
                exchange TEXT DEFAULT 'NSE',
                price REAL,
                timeframe TEXT,
                notes TEXT,
                source TEXT DEFAULT 'tradingview',
                event_time TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
            """);
        jdbcTemplate.execute("""
            CREATE INDEX IF NOT EXISTS idx_alerts_ticker_event_time
            ON alerts (ticker, event_time DESC)
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS strategy_scanner_runs (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                strategy_slug TEXT NOT NULL,
                status TEXT NOT NULL,
                message TEXT,
                started_at TEXT NOT NULL,
                completed_at TEXT,
                stocks_scanned INTEGER DEFAULT 0
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS strategy_scanner_results (
                strategy_slug TEXT NOT NULL,
                ticker TEXT NOT NULL,
                yahoo_symbol TEXT NOT NULL,
                signal TEXT NOT NULL,
                strategy TEXT NOT NULL,
                signal_date TEXT,
                last_close REAL,
                sma20 REAL,
                sma50 REAL,
                sma200 REAL,
                percent_move REAL,
                percent_below_lifetime_high REAL,
                high_52_week REAL,
                low_52_week REAL,
                buy_region INTEGER NOT NULL DEFAULT 0,
                sell_region INTEGER NOT NULL DEFAULT 0,
                scanned_at TEXT NOT NULL,
                notes TEXT,
                PRIMARY KEY (strategy_slug, ticker)
            )
            """);
    }

    private void ensureDataDirectory() {
        try {
            Files.createDirectories(Path.of("data"));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create backend data directory", exception);
        }
    }

    public long insertAlert(AlertPayload payload) {
        String now = OffsetDateTime.now().toString();
        String eventTime = payload.eventTime() != null ? payload.eventTime() : now;
        jdbcTemplate.update("""
            INSERT INTO alerts (
                ticker, action, strategy, exchange, price, timeframe,
                notes, source, event_time, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            payload.ticker().trim().toUpperCase(),
            payload.action().trim().toUpperCase(),
            payload.strategy() == null || payload.strategy().isBlank() ? "Unnamed Strategy" : payload.strategy(),
            payload.exchange() == null || payload.exchange().isBlank() ? "NSE" : payload.exchange().trim().toUpperCase(),
            payload.price(),
            payload.timeframe(),
            payload.notes(),
            payload.source() == null || payload.source().isBlank() ? "tradingview" : payload.source(),
            eventTime,
            now
        );
        return jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public List<AlertRecord> fetchRecentAlerts(int limit) {
        return jdbcTemplate.query("""
            SELECT *
            FROM alerts
            ORDER BY event_time DESC, id DESC
            LIMIT ?
            """, alertRowMapper(), limit);
    }

    public List<AlertRecord> fetchLatestAlertsPerTicker() {
        return jdbcTemplate.query("""
            SELECT a.*
            FROM alerts a
            INNER JOIN (
                SELECT ticker, MAX(event_time) AS latest_event_time
                FROM alerts
                GROUP BY ticker
            ) latest
            ON latest.ticker = a.ticker AND latest.latest_event_time = a.event_time
            ORDER BY a.event_time DESC
            """, alertRowMapper());
    }

    public long startScannerRun(StrategyType strategyType) {
        jdbcTemplate.update("""
            INSERT INTO strategy_scanner_runs (strategy_slug, status, message, started_at)
            VALUES (?, ?, ?, ?)
            """, strategyType.slug(), "RUNNING", "Scanning " + strategyType.displayName(), OffsetDateTime.now().toString());
        return jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public void finishScannerRun(long runId, String status, String message, int stocksScanned) {
        jdbcTemplate.update("""
            UPDATE strategy_scanner_runs
            SET status = ?, message = ?, completed_at = ?, stocks_scanned = ?
            WHERE id = ?
            """, status, message, OffsetDateTime.now().toString(), stocksScanned, runId);
    }

    public void upsertScannerResults(List<ScannerResult> results) {
        String scannedAt = OffsetDateTime.now().toString();
        for (ScannerResult result : results) {
            jdbcTemplate.update("""
                INSERT INTO strategy_scanner_results (
                    strategy_slug, ticker, yahoo_symbol, signal, strategy, signal_date, last_close,
                    sma20, sma50, sma200, percent_move, percent_below_lifetime_high, high_52_week, low_52_week,
                    buy_region, sell_region, scanned_at, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(strategy_slug, ticker) DO UPDATE SET
                    yahoo_symbol = excluded.yahoo_symbol,
                    signal = excluded.signal,
                    strategy = excluded.strategy,
                    signal_date = excluded.signal_date,
                    last_close = excluded.last_close,
                    sma20 = excluded.sma20,
                    sma50 = excluded.sma50,
                    sma200 = excluded.sma200,
                    percent_move = excluded.percent_move,
                    percent_below_lifetime_high = excluded.percent_below_lifetime_high,
                    high_52_week = excluded.high_52_week,
                    low_52_week = excluded.low_52_week,
                    buy_region = excluded.buy_region,
                    sell_region = excluded.sell_region,
                    scanned_at = excluded.scanned_at,
                    notes = excluded.notes
                """,
                result.strategySlug(),
                result.ticker(),
                result.yahooSymbol(),
                result.signal(),
                result.strategy(),
                result.signalDate(),
                result.lastClose(),
                result.sma20(),
                result.sma50(),
                result.sma200(),
                result.percentMove(),
                result.percentBelowLifetimeHigh(),
                result.high52Week(),
                result.low52Week(),
                result.buyRegion() ? 1 : 0,
                result.sellRegion() ? 1 : 0,
                scannedAt,
                result.notes()
            );
        }
    }

    public List<ScannerResult> fetchScannerResults(StrategyType strategyType) {
        return jdbcTemplate.query("""
            SELECT *
            FROM strategy_scanner_results
            WHERE strategy_slug = ?
            ORDER BY scanned_at DESC, ticker ASC
            """, (rs, rowNum) -> new ScannerResult(
            rs.getString("strategy_slug"),
            rs.getString("ticker"),
            rs.getString("yahoo_symbol"),
            rs.getString("signal"),
            rs.getString("strategy"),
            rs.getString("signal_date"),
            nullableDouble(rs, "last_close"),
            nullableDouble(rs, "sma20"),
            nullableDouble(rs, "sma50"),
            nullableDouble(rs, "sma200"),
            nullableDouble(rs, "percent_move"),
            nullableDouble(rs, "percent_below_lifetime_high"),
            nullableDouble(rs, "high_52_week"),
            nullableDouble(rs, "low_52_week"),
            rs.getInt("buy_region") == 1,
            rs.getInt("sell_region") == 1,
            rs.getString("scanned_at"),
            rs.getString("notes")
        ), strategyType.slug());
    }

    public ScannerRun fetchLatestScannerRun(StrategyType strategyType) {
        List<ScannerRun> runs = jdbcTemplate.query("""
            SELECT *
            FROM strategy_scanner_runs
            WHERE strategy_slug = ?
            ORDER BY started_at DESC, id DESC
            LIMIT 1
            """, (rs, rowNum) -> new ScannerRun(
            rs.getLong("id"),
            rs.getString("strategy_slug"),
            rs.getString("status"),
            rs.getString("message"),
            rs.getString("started_at"),
            rs.getString("completed_at"),
            rs.getInt("stocks_scanned")
        ), strategyType.slug());
        return runs.isEmpty() ? null : runs.getFirst();
    }

    public boolean hasScannerResults(StrategyType strategyType) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM strategy_scanner_results WHERE strategy_slug = ?",
            Integer.class,
            strategyType.slug()
        );
        return count != null && count > 0;
    }

    private RowMapper<AlertRecord> alertRowMapper() {
        return (rs, rowNum) -> new AlertRecord(
            rs.getLong("id"),
            rs.getString("ticker"),
            rs.getString("action"),
            rs.getString("strategy"),
            rs.getString("exchange"),
            nullableDouble(rs, "price"),
            rs.getString("timeframe"),
            rs.getString("notes"),
            rs.getString("source"),
            rs.getString("event_time"),
            rs.getString("created_at")
        );
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
