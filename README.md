# Indian Stock Signal Tracker

This repo contains a Spring Boot microservice and a React UI for the stock tracker.

## Structure

- [backend](/Users/suhasdeshmukh/Documents/New%20project/backend): Spring Boot + Maven microservice
- [frontend](/Users/suhasdeshmukh/Documents/New%20project/frontend): React + Vite UI
- [tradingview/sma_strategy_20_50_200.pine](/Users/suhasdeshmukh/Documents/New%20project/tradingview/sma_strategy_20_50_200.pine): Reference Pine script

## What the new stack does

- Loads watchlists from YAML resources configured in [application.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/application.yml)
- Runs local daily scanners against Yahoo Finance candles
- Supports two strategy pages:
  - `SMA`: `BUY` when `sma200 > sma50 > sma20 > close`, `SELL` when `close > sma20 > sma50 > sma200`
  - `V20`: sequence-based 20% move screener using your Pine logic
- Exposes APIs for dashboard data and scanner runs
- Shows a React dashboard with:
  - `SMA` page
  - `V20` page
  - active scanner alerts
  - email / WhatsApp / copy sharing for the visible active alerts

## Backend APIs

- `GET /api/health`
- `GET /api/dashboard?strategy=sma`
- `GET /api/dashboard?strategy=v20`
- `POST /api/scanner/run?strategy=sma`
- `POST /api/scanner/run?strategy=v20`

## Run backend

Typical local run on your machine:

```bash
cd /Users/suhasdeshmukh/Documents/New\ project/backend
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

## Notes

- The backend uses browser-style headers when calling Yahoo Finance to reduce rate-limit issues.
- The SQLite file is configured as `./data/signals.db` relative to the backend working directory.
- The repo is now Java + React only. All earlier Python prototype files have been removed.
- Watchlists are plug-and-play YAML files:
  - [v40.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/watchlists/v40.yml)
  - [v40-next.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/watchlists/v40-next.yml)
  - [v200.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/watchlists/v200.yml)
  - [bank.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/watchlists/bank.yml)
  - [nbfc.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/watchlists/nbfc.yml)
- To replace a list, edit the corresponding YAML file or point `app.watchlists.resources` in [application.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/application.yml) to a different file.
- WhatsApp and email sharing are client-side convenience actions. WhatsApp opens with prefilled text, but the final send still happens from your device/app.
- Reference Pine scripts available:
  - [tradingview/sma_strategy_20_50_200.pine](/Users/suhasdeshmukh/Documents/New%20project/tradingview/sma_strategy_20_50_200.pine)
  - [tradingview/v20.pine](/Users/suhasdeshmukh/Documents/New%20project/tradingview/v20.pine)
