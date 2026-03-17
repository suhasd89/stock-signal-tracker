package com.suhas.stocktracker.service;
import com.suhas.stocktracker.model.WatchlistGroupSummary;
import com.suhas.stocktracker.model.WatchlistStock;
import com.suhas.stocktracker.model.StoredUser;
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
            CREATE TABLE IF NOT EXISTS watchlist_stocks (
                group_name TEXT NOT NULL,
                symbol TEXT NOT NULL,
                name TEXT NOT NULL,
                yahoo_symbol TEXT NOT NULL,
                sort_order INTEGER NOT NULL DEFAULT 0,
                updated_at TEXT NOT NULL,
                PRIMARY KEY (group_name, symbol)
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS app_users (
                username TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                role TEXT NOT NULL,
                created_at TEXT NOT NULL
            )
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
                entry_price REAL,
                target_price REAL,
                sequence_start_date TEXT,
                sequence_end_date TEXT,
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
        ensureColumn("strategy_scanner_results", "entry_price", "REAL");
        ensureColumn("strategy_scanner_results", "target_price", "REAL");
        ensureColumn("strategy_scanner_results", "sequence_start_date", "TEXT");
        ensureColumn("strategy_scanner_results", "sequence_end_date", "TEXT");
    }

    private void ensureDataDirectory() {
        try {
            Files.createDirectories(Path.of("data"));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to create backend data directory", exception);
        }
    }

    private void ensureColumn(String table, String column, String type) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (Exception ignored) {
            // Column already exists on subsequent startups.
        }
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
                    sma20, sma50, sma200, percent_move, entry_price, target_price, sequence_start_date, sequence_end_date,
                    percent_below_lifetime_high, high_52_week, low_52_week,
                    buy_region, sell_region, scanned_at, notes
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
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
                    entry_price = excluded.entry_price,
                    target_price = excluded.target_price,
                    sequence_start_date = excluded.sequence_start_date,
                    sequence_end_date = excluded.sequence_end_date,
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
                result.entryPrice(),
                result.targetPrice(),
                result.sequenceStartDate(),
                result.sequenceEndDate(),
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
            nullableDouble(rs, "entry_price"),
            nullableDouble(rs, "target_price"),
            rs.getString("sequence_start_date"),
            rs.getString("sequence_end_date"),
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

    public boolean hasWatchlistStocks() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM watchlist_stocks", Integer.class);
        return count != null && count > 0;
    }

    public void replaceWatchlistGroup(String group, List<WatchlistStock> stocks) {
        jdbcTemplate.update("DELETE FROM watchlist_stocks WHERE group_name = ?", group);
        String updatedAt = OffsetDateTime.now().toString();
        for (int index = 0; index < stocks.size(); index++) {
            WatchlistStock stock = stocks.get(index);
            jdbcTemplate.update("""
                INSERT INTO watchlist_stocks (group_name, symbol, name, yahoo_symbol, sort_order, updated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                group,
                stock.symbol(),
                stock.name(),
                stock.yahooSymbol(),
                index,
                updatedAt
            );
        }
    }

    public List<WatchlistStock> fetchWatchlistStocks() {
        return jdbcTemplate.query("""
            SELECT group_name, symbol, name, yahoo_symbol
            FROM watchlist_stocks
            ORDER BY group_name ASC, sort_order ASC, symbol ASC
            """, (rs, rowNum) -> new WatchlistStock(
            rs.getString("symbol"),
            rs.getString("name"),
            rs.getString("group_name"),
            rs.getString("yahoo_symbol")
        ));
    }

    public List<WatchlistGroupSummary> fetchWatchlistGroupSummaries() {
        return jdbcTemplate.query("""
            SELECT group_name, COUNT(*) AS stock_count
            FROM watchlist_stocks
            GROUP BY group_name
            ORDER BY group_name ASC
            """, (rs, rowNum) -> new WatchlistGroupSummary(
            rs.getString("group_name"),
            rs.getInt("stock_count")
        ));
    }

    public StoredUser fetchUserByUsername(String username) {
        List<StoredUser> users = jdbcTemplate.query("""
            SELECT username, name, email, password_hash, role
            FROM app_users
            WHERE username = ?
            LIMIT 1
            """, (rs, rowNum) -> new StoredUser(
            rs.getString("username"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role")
        ), username);
        return users.isEmpty() ? null : users.getFirst();
    }

    public StoredUser fetchUserByEmail(String email) {
        List<StoredUser> users = jdbcTemplate.query("""
            SELECT username, name, email, password_hash, role
            FROM app_users
            WHERE email = ?
            LIMIT 1
            """, (rs, rowNum) -> new StoredUser(
            rs.getString("username"),
            rs.getString("name"),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("role")
        ), email);
        return users.isEmpty() ? null : users.getFirst();
    }

    public void upsertUser(StoredUser user) {
        jdbcTemplate.update("""
            INSERT INTO app_users (username, name, email, password_hash, role, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            ON CONFLICT(username) DO UPDATE SET
                name = excluded.name,
                email = excluded.email,
                password_hash = excluded.password_hash,
                role = excluded.role
            """,
            user.username(),
            user.name(),
            user.email(),
            user.passwordHash(),
            user.role(),
            OffsetDateTime.now().toString()
        );
    }

    private Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
