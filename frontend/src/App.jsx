import { useEffect, useState } from "react";

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";
const STRATEGIES = [
  { key: "sma", label: "SMA" },
  { key: "v20", label: "V20" },
];
const LISTS = [
  { key: "ALL", label: "All Lists" },
  { key: "V40", label: "V40" },
  { key: "V40 NEXT", label: "V40 Next" },
  { key: "V200", label: "V200" },
];

function formatDate(value) {
  if (!value) return "-";
  return new Date(value).toLocaleString();
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

export default function App() {
  const [dashboard, setDashboard] = useState(null);
  const [query, setQuery] = useState("");
  const [scanLoading, setScanLoading] = useState(false);
  const [bootstrapping, setBootstrapping] = useState(false);
  const [strategy, setStrategy] = useState("sma");
  const [activeList, setActiveList] = useState("ALL");

  const loadDashboard = async (strategyKey = strategy) => {
    const response = await fetch(`${API_BASE}/api/dashboard?strategy=${strategyKey}`);
    const payload = await response.json();
    setDashboard(payload);
    return payload;
  };

  const runScan = async () => {
    setScanLoading(true);
    try {
      const response = await fetch(`${API_BASE}/api/scanner/run?strategy=${strategy}`, { method: "POST" });
      if (!response.ok) {
        const payload = await response.json();
        throw new Error(payload.message || "Scanner failed");
      }
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
      await fetch(`${API_BASE}/api/scanner/run?strategy=${payload.strategy}`, { method: "POST" });
      const response = await fetch(`${API_BASE}/api/dashboard?strategy=${payload.strategy}`);
      const refreshed = await response.json();
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
    if (strategy === "sma" && activeList === "V40 NEXT") {
      setActiveList("V40");
    }
  }, [strategy, activeList]);

  if (!dashboard) {
    return <main className="shell"><div className="empty">Loading dashboard...</div></main>;
  }

  const filteredWatchlist = dashboard.watchlist.filter((row) => {
    if (activeList !== "ALL" && row.group !== activeList) {
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
      (activeList === "ALL" || row.group === activeList) &&
      (row.scannerSignal === "BUY" || row.scannerSignal === "SELL" || row.scannerSignal === "ALERT")
  );
  const totalStrategyAlerts = dashboard.watchlist.filter(
    (row) => row.scannerSignal === "BUY" || row.scannerSignal === "SELL" || row.scannerSignal === "ALERT"
  ).length;
  const visibleLists = LISTS.filter((item) => {
    if (item.key === "ALL") {
      return true;
    }
    if (strategy === "sma" && item.key === "V40 NEXT") {
      return false;
    }
    if (item.key === "V200" && !dashboard.watchlist.some((row) => row.group === "V200")) {
      return false;
    }
    return dashboard.watchlist.some((row) => row.group === item.key);
  });

  return (
    <main className="shell">
      <section className="hero">
        <div>
          <p className="eyebrow">Spring Boot + React Dashboard</p>
          <h1>Indian Stock Signal Tracker</h1>
          <p className="subcopy">
            Track your V40 companies across two historical strategy pages: SMA and V20.
          </p>
        </div>
        <div className="hero-card">
          <div className="strategy-tabs">
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
          <div className="metric">
            <span>Tracked Stocks</span>
            <strong>{dashboard.summary.trackedStocks}</strong>
          </div>
          <div className="metric">
            <span>{strategy === "sma" ? "Buy Signals" : "Active Alerts"}</span>
            <strong>{dashboard.summary.buySignals}</strong>
          </div>
          <div className="metric">
            <span>{strategy === "sma" ? "Sell Signals" : "No. of Sell Signals"}</span>
            <strong>{dashboard.summary.sellSignals}</strong>
          </div>
        </div>
      </section>

      <section className="toolbar">
        <input
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          type="search"
          placeholder={`Search ${strategy.toUpperCase()} stocks`}
        />
        <button onClick={runScan} disabled={scanLoading}>
          {scanLoading ? "Scanning..." : "Run Local Scan"}
        </button>
        <button onClick={loadDashboard}>Refresh</button>
      </section>

      <section className="panel">
        <div className="panel-header">
          <h2>Company Lists</h2>
          <p>Filter the strategy results by watchlist bucket.</p>
        </div>
        <div className="strategy-tabs">
          {visibleLists.map((item) => {
            const count = item.key === "ALL"
              ? dashboard.watchlist.length
              : dashboard.watchlist.filter((row) => row.group === item.key).length;
            return (
              <button
                key={item.key}
                className={item.key === activeList ? "tab active" : "tab"}
                onClick={() => setActiveList(item.key)}
                type="button"
              >
                {item.label} ({count})
              </button>
            );
          })}
        </div>
      </section>

      <section className="panel">
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

      <section className="panel">
        <div className="panel-header">
          <h2>
            {strategy === "sma" ? "Active SMA Alerts" : "Active V20 Alerts"} ({activeScannerAlerts.length})
          </h2>
          <p>
            {strategy === "sma"
              ? "Stocks currently in BUY or SELL region from the SMA historical scan."
              : "Stocks whose latest completed V20 green-candle sequence met the 20% threshold."}
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
              <article className="alert-card" key={row.symbol}>
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
                    Move: {row.percentMove?.toFixed(2) ?? "-"}% | 200 SMA: {row.sma200?.toFixed(2) ?? "-"} | Below lifetime high: {row.percentBelowLifetimeHigh?.toFixed(2) ?? "-"}%
                  </small>
                )}
              </article>
            ))
          )}
        </div>
      </section>

      <section className="panel">
        <div className="panel-header">
          <h2>{strategy === "sma" ? "SMA Watchlist" : "V20 Watchlist"}</h2>
          <p>
            {strategy === "sma"
              ? "Local scanner signal for each stock using your SMA 20/50/200 logic."
              : "Latest historical V20 trigger for each stock using your V20 Pine logic."}
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
                  <th>Webhook Signal</th>
                  <th>Price</th>
                  <th>Time</th>
                </tr>
              ) : (
                <tr>
                  <th>Stock</th>
                  <th>Scanner Signal</th>
                  <th>Last Close</th>
                  <th>Signal Date</th>
                  <th>Move %</th>
                  <th>200 SMA</th>
                  <th>Below Lifetime High</th>
                  <th>52W Range</th>
                </tr>
              )}
            </thead>
            <tbody>
              {filteredWatchlist.map((row) => (
                <tr key={row.symbol}>
                  <td>
                    <div className="stock-cell">
                      <strong>{row.symbol}</strong>
                      <span>{row.name} · {row.group}</span>
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
                      <td><span className={signalClass(row.webhookSignal)}>{row.webhookSignal}</span></td>
                      <td>{formatPrice(row.webhookPrice)}</td>
                      <td>{formatDate(row.webhookTime)}</td>
                    </>
                  ) : (
                    <>
                      <td><span className={signalClass(row.scannerSignal)}>{row.scannerSignal}</span></td>
                      <td>{formatPrice(row.scannerPrice)}</td>
                      <td>{formatDate(row.scannerSignalDate)}</td>
                      <td>{row.percentMove?.toFixed(2) ? `${row.percentMove.toFixed(2)}%` : "-"}</td>
                      <td>{row.sma200 ? row.sma200.toFixed(2) : "-"}</td>
                      <td>{row.percentBelowLifetimeHigh?.toFixed(2) ? `${row.percentBelowLifetimeHigh.toFixed(2)}%` : "-"}</td>
                      <td>
                        {row.low52Week && row.high52Week
                          ? `${formatPrice(row.low52Week)} - ${formatPrice(row.high52Week)}`
                          : "-"}
                      </td>
                    </>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <section className="panel">
        <div className="panel-header">
          <h2>Recent TradingView Alerts</h2>
          <p>Newest webhook alerts received by the backend.</p>
        </div>
        <div className="alerts">
          {dashboard.recentAlerts.length === 0 ? (
            <div className="empty">No webhook alerts received yet.</div>
          ) : (
            dashboard.recentAlerts.map((alert) => (
              <article className="alert-card" key={alert.id}>
                <div className="alert-head">
                  <strong>{alert.ticker}</strong>
                  <span className={signalClass(alert.action)}>{alert.action}</span>
                </div>
                <p>{alert.strategy}</p>
                <div className="alert-meta">
                  <span>{formatPrice(alert.price)}</span>
                  <span>{alert.timeframe || "Timeframe N/A"}</span>
                  <span>{formatDate(alert.eventTime)}</span>
                </div>
                {alert.notes ? <small>{alert.notes}</small> : null}
              </article>
            ))
          )}
        </div>
      </section>
    </main>
  );
}
