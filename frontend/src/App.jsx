import { useEffect, useState } from "react";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api").replace(/\/$/, "");
const STRATEGIES = [
  { key: "sma", label: "SMA" },
  { key: "v20", label: "V20" },
];
const LISTS = [
  { key: "ALL", label: "All Lists" },
  { key: "V40", label: "V40" },
  { key: "V40 NEXT", label: "V40 Next" },
  { key: "V200", label: "V200" },
  { key: "BANK", label: "Bank" },
  { key: "NBFC", label: "NBFC" },
];

function normalizeGroup(group) {
  return (group ?? "").replace(/\s+/g, "").toUpperCase();
}

function formatDate(value) {
  if (!value) return "-";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "-";
  }
  return new Intl.DateTimeFormat("en-GB", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
  }).format(date);
}

function formatPrice(value) {
  if (value === null || value === undefined || value === "") return "-";
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    maximumFractionDigits: 2,
  }).format(value);
}

function signalClass(signal) {
  if (signal === "BUY" || signal === "ALERT") return "pill buy";
  if (signal === "SELL") return "pill sell";
  return "pill neutral";
}

function tradingViewUrl(row) {
  const exchangeSymbol = row.yahooSymbol?.endsWith(".NS")
    ? row.yahooSymbol.replace(".NS", "")
    : row.symbol;
  return `https://www.tradingview.com/chart/?symbol=${encodeURIComponent(`NSE:${exchangeSymbol}`)}`;
}

function buildShareMessage(strategy, activeList, alerts) {
  const title = `${strategy.toUpperCase()} alerts${activeList === "ALL" ? "" : ` - ${activeList}`}`;
  if (alerts.length === 0) {
    return `${title}\nNo BUY or SELL signals found.`;
  }

  const rows = alerts.map((row) => {
    const extra = strategy === "sma"
      ? `Close ${formatPrice(row.scannerPrice)}`
      : `Entry ${formatPrice(row.entryPrice)} | Target ${formatPrice(row.targetPrice)}`;
    return `${row.symbol} - ${row.scannerSignal} - ${extra} - ${formatDate(row.scannerSignalDate)}`;
  });

  return [title, ...rows].join("\n");
}

export default function App() {
  const [dashboard, setDashboard] = useState(null);
  const [query, setQuery] = useState("");
  const [scanLoading, setScanLoading] = useState(false);
  const [bootstrapping, setBootstrapping] = useState(false);
  const [strategy, setStrategy] = useState("sma");
  const [activeList, setActiveList] = useState("ALL");

  const fetchJson = async (url, options) => {
    const response = await fetch(url, options);
    if (!response.ok) {
      const message = await response.text();
      throw new Error(message || `Request failed with status ${response.status}`);
    }
    return response.json();
  };

  const loadDashboard = async (strategyKey = strategy) => {
    const payload = await fetchJson(`${API_BASE}/dashboard?strategy=${strategyKey}`);
    setDashboard(payload);
    return payload;
  };

  const runScan = async () => {
    setScanLoading(true);
    try {
      await fetchJson(`${API_BASE}/scanner/run?strategy=${strategy}`, { method: "POST" });
      await loadDashboard();
    } finally {
      setScanLoading(false);
    }
  };

  const maybeBootstrapScan = async (payload) => {
    if (payload.scanner?.coverage > 0) {
      return;
    }

    setBootstrapping(true);
    try {
      await fetchJson(`${API_BASE}/scanner/run?strategy=${payload.strategy}`, { method: "POST" });
      const refreshed = await fetchJson(`${API_BASE}/dashboard?strategy=${payload.strategy}`);
      setDashboard(refreshed);
    } finally {
      setBootstrapping(false);
    }
  };

  useEffect(() => {
    loadDashboard(strategy).then(maybeBootstrapScan);
    const interval = window.setInterval(() => loadDashboard(strategy), 15000);
    return () => window.clearInterval(interval);
  }, [strategy]);

  useEffect(() => {
    if (strategy === "sma" && normalizeGroup(activeList) !== "V40" && activeList !== "ALL") {
      setActiveList("V40");
    }
  }, [strategy, activeList]);

  if (!dashboard) {
    return <main className="shell"><div className="empty">Loading dashboard...</div></main>;
  }

  const filteredWatchlist = dashboard.watchlist.filter((row) => {
    if (activeList !== "ALL" && normalizeGroup(row.group) !== normalizeGroup(activeList)) {
      return false;
    }
    const term = query.trim().toLowerCase();
    if (!term) return true;
    return (
      row.symbol.toLowerCase().includes(term) ||
      row.name.toLowerCase().includes(term) ||
      row.group.toLowerCase().includes(term) ||
      (row.scannerStrategy || "").toLowerCase().includes(term)
    );
  });

  const activeScannerAlerts = dashboard.watchlist.filter(
    (row) =>
      (activeList === "ALL" || normalizeGroup(row.group) === normalizeGroup(activeList)) &&
      (row.scannerSignal === "BUY" || row.scannerSignal === "SELL" || row.scannerSignal === "ALERT")
  );
  const totalStrategyAlerts = dashboard.watchlist.filter(
    (row) => row.scannerSignal === "BUY" || row.scannerSignal === "SELL" || row.scannerSignal === "ALERT"
  ).length;
  const shareMessage = buildShareMessage(strategy, activeList, activeScannerAlerts);
  const visibleLists = LISTS.filter((item) => {
    if (item.key === "ALL") {
      return true;
    }
    if (strategy === "sma" && item.key !== "V40") {
      return false;
    }
    if (item.key === "V200" && !dashboard.watchlist.some((row) => normalizeGroup(row.group) === "V200")) {
      return false;
    }
    return dashboard.watchlist.some((row) => normalizeGroup(row.group) === normalizeGroup(item.key));
  });

  const shareByEmail = () => {
    const subject = encodeURIComponent(`${strategy.toUpperCase()} stock alerts`);
    const body = encodeURIComponent(shareMessage);
    window.location.href = `mailto:?subject=${subject}&body=${body}`;
  };

  const shareOnWhatsApp = () => {
    const text = encodeURIComponent(shareMessage);
    window.open(`https://wa.me/?text=${text}`, "_blank", "noopener,noreferrer");
  };

  const copyAlerts = async () => {
    await navigator.clipboard.writeText(shareMessage);
  };

  const openTradingView = (row) => {
    window.open(tradingViewUrl(row), "_blank", "noopener,noreferrer");
  };

  return (
    <main className="shell">
      <header className="topbar">
        <div className="brand-lockup">
          <span className="brand-mark">ST</span>
          <div>
            <p className="eyebrow topbar-eyebrow">Signal Tracker</p>
            <strong>Indian Market Watchlists</strong>
          </div>
        </div>
        <nav className="topbar-nav" aria-label="Section navigation">
          <a href="#overview" className="nav-link">Overview</a>
          <a href="#alerts" className="nav-link">Alerts</a>
          <a href="#watchlist" className="nav-link">Watchlist</a>
          <a href="#share" className="nav-link">Share</a>
        </nav>
      </header>

      <section className="hero hero-banner">
        <div className="hero-copy">
          <p className="eyebrow">Spring Boot + React</p>
          <h1>Indian Stock Signal Tracker</h1>
          <p className="subcopy">
            A calmer workspace for your `SMA` and `V20` strategies across V40, V40 Next, V200, Bank, and NBFC lists.
          </p>
        </div>
        <div className="hero-card hero-metrics">
          <div className="strategy-tabs strategy-switch">
            {STRATEGIES.map((item) => (
              <button
                key={item.key}
                className={item.key === strategy ? "tab active" : "tab"}
                onClick={() => setStrategy(item.key)}
                type="button"
              >
                {item.label}
              </button>
            ))}
          </div>
          <div className="metric-grid">
            <div className="metric">
              <span>Tracked Stocks</span>
              <strong>{dashboard.summary.trackedStocks}</strong>
            </div>
            <div className="metric buy-tint">
              <span>{strategy === "sma" ? "Buy Signals" : "Active Alerts"}</span>
              <strong>{dashboard.summary.buySignals}</strong>
            </div>
            <div className="metric sell-tint">
              <span>{strategy === "sma" ? "Sell Signals" : "Sell Hits"}</span>
              <strong>{dashboard.summary.sellSignals}</strong>
            </div>
          </div>
        </div>
      </section>

      <section className="panel nav-panel" id="overview">
        <div className="nav-grid">
          <div className="nav-block">
            <span className="nav-label">Strategy</span>
            <div className="strategy-tabs strategy-switch strategy-row">
              {STRATEGIES.map((item) => (
                <button
                  key={item.key}
                  className={item.key === strategy ? "tab active" : "tab"}
                  onClick={() => setStrategy(item.key)}
                  type="button"
                >
                  {item.label}
                </button>
              ))}
            </div>
          </div>
          <div className="nav-block">
            <span className="nav-label">Lists</span>
            <div className="list-tabs compact-tabs">
              {visibleLists.map((item) => {
                const count = item.key === "ALL"
                  ? dashboard.watchlist.length
                  : dashboard.watchlist.filter((row) => normalizeGroup(row.group) === normalizeGroup(item.key)).length;
                return (
                  <button
                    key={item.key}
                    className={item.key === activeList ? "tab list-tab active" : "tab list-tab"}
                    onClick={() => setActiveList(item.key)}
                    type="button"
                  >
                    <span>{item.label}</span>
                    <strong>{count}</strong>
                  </button>
                );
              })}
            </div>
          </div>
          <div className="nav-block search-block">
            <span className="nav-label">Search</span>
            <input
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              type="search"
              placeholder={`Search ${strategy.toUpperCase()} stocks`}
            />
          </div>
          <div className="nav-block actions-block">
            <span className="nav-label">Actions</span>
            <div className="toolbar-actions">
              <button onClick={runScan} disabled={scanLoading}>
                {scanLoading ? "Scanning..." : "Run Local Scan"}
              </button>
              <button className="secondary-button" onClick={() => loadDashboard()}>Refresh</button>
            </div>
          </div>
        </div>
      </section>

      <section className="panel panel-accent" id="share">
        <div className="panel-header">
          <h2>Share Alerts</h2>
          <p>Send the currently visible BUY and SELL signals through your own email app or WhatsApp.</p>
        </div>
        <div className="action-row">
          <button type="button" onClick={shareByEmail}>Share by Email</button>
          <button type="button" onClick={shareOnWhatsApp}>Share on WhatsApp</button>
          <button type="button" onClick={copyAlerts}>Copy Alert Text</button>
        </div>
      </section>

      <section className="panel panel-status">
        <div className="panel-header">
          <h2>Scanner Status</h2>
          <p>
            {dashboard.scanner.lastRun
              ? `${dashboard.scanner.lastRun.status} - ${dashboard.scanner.lastRun.message} Last run: ${formatDate(
                  dashboard.scanner.lastRun.completedAt || dashboard.scanner.lastRun.startedAt
                )}. Coverage: ${dashboard.scanner.coverage} stocks.`
              : bootstrapping
              ? "Running initial scanner load..."
              : "No local scan has been run yet."}
          </p>
        </div>
      </section>

      <section className="panel" id="alerts">
        <div className="panel-header">
          <h2>
            {strategy === "sma" ? "Active SMA Alerts" : "Active V20 Alerts"} ({activeScannerAlerts.length})
          </h2>
          <p>
            {strategy === "sma"
              ? "Stocks currently in BUY or SELL region from the SMA historical scan."
              : "Stocks whose latest valid V20 setup met the 20% threshold and passed the list-specific rules."}
          </p>
        </div>
        <div className="alerts">
          {activeScannerAlerts.length === 0 ? (
            <div className="empty">
              {bootstrapping
                ? "Running initial scan. Active alerts will appear shortly."
                : totalStrategyAlerts > 0
                ? `No alerts match the current ${activeList} filter. ${totalStrategyAlerts} alerts exist in ${strategy.toUpperCase()} overall.`
                : "No active local scanner alerts right now."}
            </div>
          ) : (
            activeScannerAlerts.map((row) => (
              <article
                className="alert-card clickable-card"
                key={`${row.group}-${row.symbol}`}
                onClick={() => openTradingView(row)}
                onKeyDown={(event) => {
                  if (event.key === "Enter" || event.key === " ") {
                    event.preventDefault();
                    openTradingView(row);
                  }
                }}
                role="button"
                tabIndex={0}
              >
                <div className="alert-head">
                  <strong>{row.symbol}</strong>
                  <span className={signalClass(row.scannerSignal)}>{row.scannerSignal}</span>
                </div>
                <p>{row.name}</p>
                <div className="alert-meta">
                  <span>{formatPrice(row.scannerPrice)}</span>
                  <span>{formatDate(row.scannerSignalDate)}</span>
                </div>
                {strategy === "sma" ? (
                  <small>
                    20 SMA: {row.sma20?.toFixed(2) ?? "-"} | 50 SMA: {row.sma50?.toFixed(2) ?? "-"} | 200 SMA: {row.sma200?.toFixed(2) ?? "-"}
                  </small>
                ) : (
                  <small>
                    Entry: {formatPrice(row.entryPrice)} | Target: {formatPrice(row.targetPrice)} | Move: {row.percentMove?.toFixed(2) ?? "-"}%
                  </small>
                )}
              </article>
            ))
          )}
        </div>
      </section>

      <section className="panel" id="watchlist">
        <div className="panel-header">
          <h2>{strategy === "sma" ? "SMA Watchlist" : "V20 Watchlist"}</h2>
          <p>
            {strategy === "sma"
              ? "Local scanner signal for each stock using your SMA 20/50/200 logic."
              : "Latest valid V20 setup for each stock, including entry, target, and formation window."}
          </p>
        </div>
        <div className="table-wrap">
          <table>
            <thead>
              {strategy === "sma" ? (
                <tr>
                  <th>Stock</th>
                  <th>Scanner Signal</th>
                  <th>Last Daily Close</th>
                  <th>Signal Date</th>
                  <th>SMA Stack</th>
                </tr>
              ) : (
                <tr>
                  <th>Stock</th>
                  <th>Scanner Signal</th>
                  <th>Last Close</th>
                  <th>Formation End</th>
                  <th>Entry Price</th>
                  <th>Target Price</th>
                  <th>Move %</th>
                  <th>Formation Window</th>
                  <th>200 SMA</th>
                </tr>
              )}
            </thead>
            <tbody>
              {filteredWatchlist.map((row) => (
                <tr key={`${row.group}-${row.symbol}`}>
                  <td>
                    <div
                      className="stock-cell clickable-stock"
                      onClick={() => openTradingView(row)}
                      onKeyDown={(event) => {
                        if (event.key === "Enter" || event.key === " ") {
                          event.preventDefault();
                          openTradingView(row);
                        }
                      }}
                      role="button"
                      tabIndex={0}
                    >
                      <strong>{row.symbol}</strong>
                      <span>{row.name} · {normalizeGroup(row.group) === "V40NEXT" ? "V40 NEXT" : row.group}</span>
                    </div>
                  </td>
                  {strategy === "sma" ? (
                    <>
                      <td><span className={signalClass(row.scannerSignal)}>{row.scannerSignal}</span></td>
                      <td>{formatPrice(row.scannerPrice)}</td>
                      <td>{formatDate(row.scannerSignalDate)}</td>
                      <td>
                        {row.sma20
                          ? `20: ${row.sma20.toFixed(2)} | 50: ${row.sma50.toFixed(2)} | 200: ${row.sma200.toFixed(2)}`
                          : "-"}
                      </td>
                    </>
                  ) : (
                    <>
                      <td><span className={signalClass(row.scannerSignal)}>{row.scannerSignal}</span></td>
                      <td>{formatPrice(row.scannerPrice)}</td>
                      <td>{formatDate(row.scannerSignalDate)}</td>
                      <td>{formatPrice(row.entryPrice)}</td>
                      <td>{formatPrice(row.targetPrice)}</td>
                      <td>{row.percentMove?.toFixed(2) ? `${row.percentMove.toFixed(2)}%` : "-"}</td>
                      <td>
                        {row.sequenceStartDate && row.sequenceEndDate
                          ? `${formatDate(row.sequenceStartDate)} -> ${formatDate(row.sequenceEndDate)}`
                          : "-"}
                      </td>
                      <td>{row.sma200 ? row.sma200.toFixed(2) : "-"}</td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </main>
  );
}
