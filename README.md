# Indian Stock Signal Tracker

This repo contains a Spring Boot microservice and a React UI for the stock tracker.

## Structure

- [backend](/Users/suhasdeshmukh/Documents/New%20project/backend): Spring Boot + Maven microservice
- [frontend](/Users/suhasdeshmukh/Documents/New%20project/frontend): React + Vite UI
- [tradingview/sma_strategy_20_50_200.pine](/Users/suhasdeshmukh/Documents/New%20project/tradingview/sma_strategy_20_50_200.pine): Reference Pine script

## What the new stack does

- Loads watchlists from YAML resources configured in [application.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/application.yml)
- Runs local daily scanners against Yahoo Finance candles
- Stores watchlists in SQLite after an initial YAML seed
- Supports two strategy pages:
  - `SMA`: `BUY` when `sma200 > sma50 > sma20 > close`, `SELL` when `close > sma20 > sma50 > sma200`
  - `V20`: sequence-based 20% move screener using your Pine logic
- Exposes APIs for dashboard data and scanner runs
- Exposes APIs for watchlist administration
- Shows a React dashboard with:
  - `SMA` page
  - `V20` page
  - active scanner alerts
  - watchlist manager for `V40`, `V40 Next`, `V200`, `Bank`, and `NBFC`
  - email / WhatsApp / copy sharing for the visible active alerts

## Backend APIs

- `GET /api/health`
- `GET /api/dashboard?strategy=sma`
- `GET /api/dashboard?strategy=v20`
- `POST /api/scanner/run?strategy=sma`
- `POST /api/scanner/run?strategy=v20`
- `GET /api/watchlists`
- `POST /api/watchlists/replace`

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

## Run with Docker

You can also run the full app with Docker Compose from the project root:

```bash
cd /Users/suhasdeshmukh/Documents/New\ project
docker compose up --build
```

Then open:

- Frontend: `http://localhost:3000`
- Backend API: `http://localhost:8080/api/health`

Useful Docker commands:

```bash
docker compose up --build -d
docker compose logs -f
docker compose down
```

Notes:

- The frontend container serves the built React app through Nginx.
- Nginx proxies `/api/*` requests to the backend container.
- SQLite data is persisted in the Docker volume `backend-data`, so your scanner history remains even if the containers are recreated.

## Notes

- The backend uses browser-style headers when calling Yahoo Finance to reduce rate-limit issues.
- The SQLite file is configured as `./data/signals.db` relative to the backend working directory.
- The repo is now Java + React only. All earlier Python prototype files have been removed.
- Watchlists are plug-and-play YAML files:
- YAML files now act as the initial seed for the database-backed watchlists:
  - [v40.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/watchlists/v40.yml)
  - [v40-next.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/watchlists/v40-next.yml)
  - [v200.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/watchlists/v200.yml)
  - [bank.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/watchlists/bank.yml)
  - [nbfc.yml](/Users/suhasdeshmukh/Documents/New%20project/backend/src/main/resources/watchlists/nbfc.yml)
- After first startup, you can replace a list directly from the UI by pasting one company per line into the `Watchlist Manager`.
- WhatsApp and email sharing are client-side convenience actions. WhatsApp opens with prefilled text, but the final send still happens from your device/app.
- Reference Pine scripts available:
  - [tradingview/sma_strategy_20_50_200.pine](/Users/suhasdeshmukh/Documents/New%20project/tradingview/sma_strategy_20_50_200.pine)
  - [tradingview/v20.pine](/Users/suhasdeshmukh/Documents/New%20project/tradingview/v20.pine)
