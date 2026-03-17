import { useEffect, useState } from "react";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080/api").replace(/\/$/, "");
const STRATEGIES = [
  { key: "sma", label: "SMA" },
  { key: "v20", label: "V20" },
];
const MANAGEABLE_LISTS = [
  { key: "V40", label: "V40" },
  { key: "V40 NEXT", label: "V40 Next" },
  { key: "V200", label: "V200" },
  { key: "BANK", label: "Bank" },
  { key: "NBFC", label: "NBFC" },
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
  const [currentUser, setCurrentUser] = useState(undefined);
  const [dashboard, setDashboard] = useState(null);
  const [watchlistAdmin, setWatchlistAdmin] = useState(null);
  const [page, setPage] = useState("dashboard");
  const [query, setQuery] = useState("");
  const [scanLoading, setScanLoading] = useState(false);
  const [bootstrapping, setBootstrapping] = useState(false);
  const [savingList, setSavingList] = useState(false);
  const [strategy, setStrategy] = useState("sma");
  const [activeList, setActiveList] = useState("ALL");
  const [manageGroup, setManageGroup] = useState("V200");
  const [manageText, setManageText] = useState("");
  const [manageMessage, setManageMessage] = useState("");
  const [statusMessage, setStatusMessage] = useState("");
  const [authMode, setAuthMode] = useState("login");
  const [authLoading, setAuthLoading] = useState(false);
  const [authError, setAuthError] = useState("");
  const [authSuccess, setAuthSuccess] = useState("");
  const [authForm, setAuthForm] = useState({
    username: "",
    password: "",
    name: "",
    email: "",
  });

  const isAdmin = currentUser?.role === "ADMIN";

  const fetchJson = async (url, options, config = {}) => {
    const response = await fetch(url, {
      credentials: "include",
      ...options,
    });
    if (!response.ok) {
      const raw = await response.text();
      let message = raw;
      try {
        const parsed = raw ? JSON.parse(raw) : null;
        message = parsed?.message || message;
      } catch {
        // keep raw text fallback
      }
      if (response.status === 401 && !config.allowUnauthorized) {
        setCurrentUser(null);
        setDashboard(null);
        setWatchlistAdmin(null);
      }
      throw new Error(message || `Request failed with status ${response.status}`);
    }
    return response.json();
  };

  const loadDashboard = async (strategyKey = strategy) => {
    const payload = await fetchJson(`${API_BASE}/dashboard?strategy=${strategyKey}`);
    setDashboard(payload);
    return payload;
  };

  const loadWatchlists = async () => {
    const payload = await fetchJson(`${API_BASE}/watchlists`);
    setWatchlistAdmin(payload);
    return payload;
  };

  const loadSession = async () => {
    try {
      const payload = await fetchJson(`${API_BASE}/auth/me`, undefined, { allowUnauthorized: true });
      setCurrentUser(payload.user);
      return payload.user;
    } catch {
      setCurrentUser(null);
      return null;
    }
  };

  const runScan = async () => {
    setScanLoading(true);
    try {
      await fetchJson(`${API_BASE}/scanner/run?strategy=${strategy}`, { method: "POST" });
      await loadDashboard();
      setStatusMessage("Scan completed successfully.");
    } finally {
      setScanLoading(false);
    }
  };

  const refreshAll = async () => {
    if (isAdmin) {
      await Promise.all([loadDashboard(), loadWatchlists()]);
    } else {
      await loadDashboard();
    }
    setStatusMessage("Dashboard refreshed successfully.");
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
    loadSession();
  }, []);

  useEffect(() => {
    if (!currentUser) {
      return;
    }

    loadDashboard(strategy).then(maybeBootstrapScan);
    if (isAdmin) {
      loadWatchlists();
    } else {
      setWatchlistAdmin(null);
    }

    const interval = window.setInterval(() => loadDashboard(strategy), 15000);
    return () => window.clearInterval(interval);
  }, [currentUser, strategy, isAdmin]);

  useEffect(() => {
    if (strategy === "sma" && normalizeGroup(activeList) !== "V40" && activeList !== "ALL") {
      setActiveList("V40");
    }
  }, [strategy, activeList]);

  useEffect(() => {
    if (!isAdmin && page === "manage") {
      setPage("dashboard");
    }
  }, [isAdmin, page]);

  const updateAuthField = (field, value) => {
    setAuthForm((current) => ({ ...current, [field]: value }));
  };

  const login = async () => {
    setAuthLoading(true);
    setAuthError("");
    setAuthSuccess("");
    try {
      const payload = await fetchJson(`${API_BASE}/auth/login`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          username: authForm.username,
          password: authForm.password,
        }),
      }, { allowUnauthorized: true });
      setCurrentUser(payload.user);
      setAuthSuccess("Login successful.");
      setStatusMessage("Welcome back.");
      setAuthForm((current) => ({ ...current, password: "" }));
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "Login failed.");
    } finally {
      setAuthLoading(false);
    }
  };

  const signup = async () => {
    setAuthLoading(true);
    setAuthError("");
    setAuthSuccess("");
    try {
      const payload = await fetchJson(`${API_BASE}/auth/signup`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          username: authForm.username,
          password: authForm.password,
          name: authForm.name,
          email: authForm.email,
        }),
      }, { allowUnauthorized: true });
      setAuthSuccess(payload.message || "Account created successfully. Please sign in.");
      setAuthMode("login");
      setAuthForm((current) => ({ ...current, password: "" }));
    } catch (error) {
      setAuthError(error instanceof Error ? error.message : "Signup failed.");
    } finally {
      setAuthLoading(false);
    }
  };

  const logout = async () => {
    try {
      await fetchJson(`${API_BASE}/auth/logout`, { method: "POST" }, { allowUnauthorized: true });
    } catch {
      // ignore and clear local state anyway
    }
    setCurrentUser(null);
    setDashboard(null);
    setWatchlistAdmin(null);
    setPage("dashboard");
    setStatusMessage("Logged out successfully.");
  };

  if (currentUser === undefined) {
    return <main className="shell"><div className="empty">Loading dashboard...</div></main>;
  }

  if (!currentUser) {
    return (
      <main className="shell auth-shell">
        <section className="auth-stage">
          <div className="auth-copy">
            <p className="eyebrow">Secure Stock Workspace</p>
            <h1>Track signals with a private login.</h1>
            <p className="subcopy">
              Sign in to access your market dashboard, active alerts, and saved watchlists. Only the admin account can manage and replace list data.
            </p>
            <div className="auth-highlights">
              <div className="auth-highlight">
                <strong>Private access</strong>
                <span>Your dashboard and scans stay behind login.</span>
              </div>
              <div className="auth-highlight">
                <strong>Role-based pages</strong>
                <span>Only admins can see and edit company lists.</span>
              </div>
              <div className="auth-highlight">
                <strong>Live scanners</strong>
                <span>SMA and V20 views stay ready after sign-in.</span>
              </div>
            </div>
          </div>
          <div className="auth-card">
            <div className="auth-card-head">
              <p className="eyebrow">Welcome Back</p>
              <h2>{authMode === "login" ? "Sign in to continue" : "Create your account"}</h2>
            </div>
            <div className="auth-tabs">
              <button type="button" className={authMode === "login" ? "tab active" : "tab"} onClick={() => setAuthMode("login")}>Sign In</button>
              <button type="button" className={authMode === "signup" ? "tab active" : "tab"} onClick={() => setAuthMode("signup")}>Sign Up</button>
            </div>
            <div className="auth-form">
              {authMode === "signup" ? (
                <>
                  <label className="field-label">
                    <span>Name</span>
                    <input value={authForm.name} onChange={(event) => updateAuthField("name", event.target.value)} type="text" placeholder="Your full name" />
                  </label>
                  <label className="field-label">
                    <span>Email</span>
                    <input value={authForm.email} onChange={(event) => updateAuthField("email", event.target.value)} type="email" placeholder="name@email.com" />
                  </label>
                </>
              ) : null}
              <label className="field-label">
                <span>Username</span>
                <input value={authForm.username} onChange={(event) => updateAuthField("username", event.target.value)} type="text" placeholder="Username" />
              </label>
              <label className="field-label">
                <span>Password</span>
                <input value={authForm.password} onChange={(event) => updateAuthField("password", event.target.value)} type="password" placeholder="Password" />
              </label>
              {authMode === "signup" ? (
                <small>Password must be 8+ chars with uppercase, lowercase, number, and special character.</small>
              ) : null}
              {authError ? <div className="auth-message auth-error">{authError}</div> : null}
              {authSuccess ? <div className="auth-message auth-success">{authSuccess}</div> : null}
              <button type="button" onClick={authMode === "login" ? login : signup} disabled={authLoading}>
                {authLoading ? "Please wait..." : authMode === "login" ? "Login" : "Create Account"}
              </button>
            </div>
          </div>
        </section>
      </main>
    );
  }

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

  const saveWatchlist = async () => {
    setSavingList(true);
    setManageMessage("");
    setStatusMessage("");
    try {
      const payload = await fetchJson(`${API_BASE}/watchlists/replace`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
        },
        body: JSON.stringify({
          group: manageGroup,
          rawText: manageText,
        }),
      });
      const nextStrategy = normalizeGroup(manageGroup) === "V40" ? "sma" : "v20";
      setStrategy(nextStrategy);
      setActiveList(manageGroup);
      await Promise.all([loadWatchlists(), loadDashboard(nextStrategy)]);
      const message = payload.guessedSymbols?.length
        ? `${payload.message} List saved successfully. Guessed: ${payload.guessedSymbols.join(", ")}`
        : `${payload.message} List saved successfully.`;
      setManageMessage(message);
      setStatusMessage(message);
    } catch (error) {
      const message = error instanceof Error ? error.message : "Failed to save watchlist.";
      setManageMessage(message);
      setStatusMessage(message);
    } finally {
      setSavingList(false);
    }
  };

  const currentManagedCount = watchlistAdmin?.groups?.find((item) => normalizeGroup(item.group) === normalizeGroup(manageGroup))?.count ?? 0;
  const currentManagedStocks = watchlistAdmin?.stocks?.filter((item) => normalizeGroup(item.group) === normalizeGroup(manageGroup)) ?? [];

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
          <button type="button" className={page === "dashboard" ? "nav-link nav-link-button active" : "nav-link nav-link-button"} onClick={() => setPage("dashboard")}>Dashboard</button>
          {isAdmin ? (
            <button type="button" className={page === "manage" ? "nav-link nav-link-button active" : "nav-link nav-link-button"} onClick={() => setPage("manage")}>Manage Lists</button>
          ) : null}
          <span className="user-pill">{currentUser.name} · {currentUser.role}</span>
          <button type="button" className="nav-link nav-link-button" onClick={logout}>Logout</button>
        </nav>
      </header>

      {statusMessage ? <div className="status-banner">{statusMessage}</div> : null}

      {page === "dashboard" ? (
        <>
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
              <button className="secondary-button" onClick={refreshAll}>Refresh</button>
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
          <h2>{strategy === "sma" ? "SMA Watchlist" : "V20 Watchlist"} ({filteredWatchlist.length})</h2>
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
        </>
      ) : (
        <>
      <section className="hero hero-banner manage-hero">
        <div className="hero-copy">
          <p className="eyebrow">Database-backed Watchlists</p>
          <h1>Manage Company Lists</h1>
          <p className="subcopy">
            Keep list maintenance separate from the scanning dashboard. Paste one company per line, save the selected bucket, and then jump back to review the updated watchlist.
          </p>
        </div>
        <div className="hero-card hero-metrics">
          <div className="metric-grid single-column-grid">
            <div className="metric">
              <span>Selected List</span>
              <strong>{manageGroup}</strong>
            </div>
            <div className="metric buy-tint">
              <span>Stored Companies</span>
              <strong>{currentManagedCount}</strong>
            </div>
          </div>
        </div>
      </section>

      <section className="panel panel-manage roomy-panel">
        <div className="panel-header">
          <h2>Watchlist Editor</h2>
          <p>Choose the target group, paste the list, and save. This replaces that group in the database.</p>
        </div>
        <div className="manage-grid">
          <div className="nav-block">
            <span className="nav-label">List Type</span>
            <div className="list-tabs compact-tabs">
              {MANAGEABLE_LISTS.map((item) => (
                <button
                  key={item.key}
                  type="button"
                  className={manageGroup === item.key ? "tab list-tab active" : "tab list-tab"}
                  onClick={() => setManageGroup(item.key)}
                >
                  <span>{item.label}</span>
                </button>
              ))}
            </div>
          </div>
          <div className="manage-sidecard">
            <span className="nav-label">Current Stored Count</span>
            <strong>{currentManagedCount}</strong>
            <small>After saving, the app returns to the dashboard and opens the updated list.</small>
          </div>
        </div>
        <textarea
          className="manage-textarea"
          value={manageText}
          onChange={(event) => setManageText(event.target.value)}
          placeholder={`Paste ${manageGroup} companies here, one per line`}
        />
        <div className="toolbar-actions">
          <button type="button" onClick={saveWatchlist} disabled={savingList || !manageText.trim()}>
            {savingList ? "Saving..." : `Save ${manageGroup}`}
          </button>
          <button type="button" className="secondary-button" onClick={() => setPage("dashboard")}>Back to Dashboard</button>
        </div>
        {manageMessage ? <div className="empty manager-message">{manageMessage}</div> : null}
      </section>

      <section className="panel roomy-panel">
        <div className="panel-header">
          <h2>{manageGroup} Current Entries ({currentManagedStocks.length})</h2>
          <p>This preview is read from the database and helps confirm whether your latest save actually updated the list.</p>
        </div>
        <div className="manage-preview-grid">
          {currentManagedStocks.map((stock) => (
            <article key={`${stock.group}-${stock.symbol}`} className="preview-card">
              <strong>{stock.symbol}</strong>
              <p>{stock.name}</p>
              <small>{stock.yahooSymbol}</small>
            </article>
          ))}
        </div>
      </section>
        </>
      )}
    </main>
  );
}
