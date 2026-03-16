# Indian Stock Signal Tracker

This repo contains a Spring Boot microservice and a React UI for the stock tracker.

## Structure

- [backend](/Users/suhasdeshmukh/Documents/New%20project/backend): Spring Boot + Maven microservice
- [frontend](/Users/suhasdeshmukh/Documents/New%20project/frontend): React + Vite UI
- [tradingview/sma_strategy_20_50_200.pine](/Users/suhasdeshmukh/Documents/New%20project/tradingview/sma_strategy_20_50_200.pine): TradingView Pine script

## What the new stack does

- Loads your V40 watchlist from [backend/src/main/resources/watchlist.json](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/watchlist.json)
- Stores TradingView webhook alerts and scanner results in SQLite
- Runs local daily scanners against Yahoo Finance candles
- Supports two strategy pages:
  - `SMA`: `BUY` when `sma200 > sma50 > sma20 > close`, `SELL` when `close > sma20 > sma50 > sma200`
  - `V20`: sequence-based 20% move screener using your Pine logic
- Exposes APIs for dashboard data, scanner runs, and TradingView webhook ingestion
- Shows a React dashboard with:
  - `SMA` page
  - `V20` page
  - active scanner alerts
  - recent TradingView alerts

## Backend APIs

- `GET /api/health`
- `GET /api/dashboard?strategy=sma`
- `GET /api/dashboard?strategy=v20`
- `POST /api/scanner/run?strategy=sma`
- `POST /api/scanner/run?strategy=v20`
- `POST /api/tradingview/webhook`

## Run backend

Typical local run on your machine:

```bash
cd /Users/suhasdeshmukh/Documents/New\ project/backend
export WEBHOOK_SECRET="my-super-secret-123"
mvn spring-boot:run
```

The backend defaults to port `8080`.

## Run frontend

Typical local run on your machine:

```bash
cd /Users/suhasdeshmukh/Documents/New\ project/frontend
npm install
npm run dev
```

The frontend defaults to port `5173` and calls `http://localhost:8080` unless you override:

```bash
VITE_API_BASE_URL=http://localhost:8080
```

## TradingView webhook payload

```json
{
  "secret": "my-super-secret-123",
  "ticker": "{{ticker}}",
  "exchange": "{{exchange}}",
  "action": "BUY",
  "strategy": "SMA Strategy: 20/50/200",
  "price": "{{close}}",
  "timeframe": "{{interval}}",
  "eventTime": "{{timenow}}",
  "notes": "Buy signal from SMA alignment"
}
```

For sell alerts, change `action` to `SELL`.

## Notes

- The backend uses browser-style headers when calling Yahoo Finance to reduce rate-limit issues.
- The SQLite file is configured as `./data/signals.db` relative to the backend working directory.
- The repo is now Java + React only. All earlier Python prototype files have been removed.
- TradingView scripts available:
  - [tradingview/sma_strategy_20_50_200.pine](/Users/suhasdeshmukh/Documents/New%20project/tradingview/sma_strategy_20_50_200.pine)
  - [tradingview/v20.pine](/Users/suhasdeshmukh/Documents/New%20project/tradingview/v20.pine)
