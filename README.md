# VPS Controller

A small dashboard that answers two questions about my server: **which containers are not fine right now?**
(V1) and **which of my HTTP endpoints is not answering?** (V2).

Spring Boot reads the container list from the Docker Engine API, turns Docker's raw state and free-text
status into one unambiguous state; separately it probes configured HTTP endpoints on a schedule, keeps their
state and history in PostgreSQL and alerts on a state change. A React page lists the problems first.

## Container states (V1)

| State | Meaning |
|---|---|
| **Läuft** | running, and either healthy or without a health check |
| **Gestört** | unhealthy, restart loop, crashed (exit ≠ 0) or dead |
| **Gestoppt** | exited cleanly (exit 0), paused or never started |
| **Unbekannt** | health check still starting, unknown Docker state, missing exit code |
| **Nicht erreichbar** | the Docker source did not answer usably — the API returns 503, never an empty list |

Rule: only a container that is *proven* to run is shown as «Läuft». Anything uncertain is something else.

## Availability checks (V2)

V2 exists to replace Uptime Kuma: the same job (is my endpoint up, how has it behaved lately, tell me when it
changes) done with fewer moving parts — one Spring Boot app instead of a separate Node service, checks defined
in a file in this repo, history in PostgreSQL, and the same read-only Docker state V1 already shows.

**State machine** (pure class `CheckStateMachine`, table-tested):

| State | Label | Goes to |
|---|---|---|
| `PENDING` | Wird geprüft | `UP` on the first success, `DOWN` after `failureThreshold` consecutive failures |
| `UP` | Erreichbar | `DOWN` after `failureThreshold` consecutive failures; a single blip does not count |
| `DOWN` | Ausgefallen | `UP` after `recoveryThreshold` consecutive successes |
| `PAUSED` | Pausiert | a check that is no longer in the checks file; its history is kept, not deleted |

**How it works**

- Checks come from a YAML file (`vps.checks.file`, default `./checks.yml`). On startup they are upserted by
  name into `http_check`; a check removed from the file is marked disabled and shown as `PAUSED`.
- One scheduler tick every 5 s runs every due check concurrently on **virtual threads**, at most once in flight.
- The probe is the JDK `HttpClient` (normal redirects, connect + request timeout). A response outside the
  configured `accept` range is a failure; timeouts, DNS/connection errors and TLS errors each get a short
  German message (`HTTP 525 (erwartet 200-399)`, `Zeitüberschreitung nach 10 s`, `Keine Verbindung: …`, `TLS-Fehler: …`).
- Every result goes into `check_result`; state and counters live in `check_state`. Uptime (24 h / 30 d) and
  the 30-minute history buckets are computed in SQL. A daily job deletes results older than 30 days.
- Alerts: `TelegramNotifier` when `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` are set, otherwise `LogNotifier`.
  A send failure is logged, retried once and never breaks checking; the token is never logged.
- Dead-man switch: if `vps.heartbeat.url` is set it is pinged after each successful cycle, at most once per 60 s.

## Architecture

```
React (Vite, TS) ──/api──▶ Spring Boot 4 (Java 25) ──HTTP GET──▶ docker-socket-proxy ──▶ Docker     (V1)
   localhost:5173            127.0.0.1:8080               HTTP GET──▶ https://…  (checks, V2)
                                                          JDBC ─────▶ PostgreSQL (state + history)
                                                          HTTPS ────▶ api.telegram.org (alerts, optional)
```

- `ContainerStatusMapper` / `DockerClient` / `ContainerService` / `ContainerController` – V1, unchanged.
- `CheckStateMachine` – the state rules above, no I/O.
- `HttpProbe` – one GET with the configured timeout; classifies the outcome.
- `CheckScheduler` – the 5 s tick; `CheckRunner` runs one check end-to-end (probe → state machine → persist → notify).
- `CheckRepository` – all SQL for definitions, state, results, uptime and buckets.
- `CheckController` – `GET /api/checks` and `GET /api/checks/{id}/history`.

## API

`GET /api/containers` — see the V1 contract below.

```json
{ "checkedAt": "2026-11-22T20:00:00Z",
  "containers": [ { "name": "api", "image": "app:1", "state": "DEGRADED", "label": "Gestört",
                    "detail": "Health-Check schlägt fehl" } ] }
```

Source unreachable → `503 application/problem+json`:
`{ "title": "Nicht erreichbar", "status": 503, "detail": "…", "state": "UNREACHABLE", "checkedAt": "…" }`

`GET /api/checks` — sorted by urgency (`DOWN`, `PENDING`, `UP`, `PAUSED`), then name:

```json
{ "checkedAt": "2026-11-23T10:00:00Z",
  "checks": [ { "id": 3, "name": "Shop", "url": "https://shop.example.org/health", "state": "DOWN",
                "label": "Ausgefallen", "since": "2026-11-23T09:41:00Z",
                "lastCheckedAt": "2026-11-23T09:59:30Z", "lastStatusCode": 525, "lastLatencyMs": null,
                "lastError": "HTTP 525 (erwartet 200-399)", "uptime24h": 0.0, "uptime30d": 61.3 } ] }
```

`GET /api/checks/{id}/history?hours=24` (`hours` 1..720, default 24) — fixed 30-minute windows, oldest first;
empty windows have `total: 0` and `avgLatencyMs: null`. Unknown id → `404` ProblemDetail, bad `hours` → `400`.

```json
{ "checkId": 3, "from": "2026-11-22T10:00:00Z", "to": "2026-11-23T10:00:00Z",
  "buckets": [ { "start": "2026-11-22T10:00:00Z", "total": 30, "failed": 0, "avgLatencyMs": 81 } ] }
```

`GET /api/health` → `{ "ok": true }` (used by the container healthcheck).

## Configuration (environment variables)

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/vpscontroller` | PostgreSQL for state and history |
| `DB_USER` / `DB_PASSWORD` | `vpscontroller` / *(empty)* | database credentials |
| `VPS_DOCKER_URL` | `http://127.0.0.1:2375` | Docker source for V1 (docker-socket-proxy) |
| `VPS_CHECKS_FILE` | `./checks.yml` | checks YAML; a missing file means zero checks, not a failure |
| `HEARTBEAT_URL` | *(empty)* | dead-man switch, pinged after each successful cycle |
| `TELEGRAM_BOT_TOKEN` / `TELEGRAM_CHAT_ID` | *(empty)* | Telegram alerts; without them alerts only go to the log |

The app starts and serves `/api/containers` even when the checks file is missing (a warning is logged, zero
checks are active).

## Deploy artefacts (in the repo, not deployed by it)

- `backend/Dockerfile` – multi-stage, builds the React UI and Spring API together for `linux/amd64`, runs as a
  non-root user on `eclipse-temurin:25-jre-alpine` with `-Xmx256m -XX:+UseSerialGC`, and exposes the bundled UI
  at `/` plus the API under `/api`. Build from the repository root:
  `docker build --platform linux/amd64 -f backend/Dockerfile -t vps-controller:latest .`.
- `deploy/vps-controller.compose.yml` – `app` + `db` (`postgres:17.11-alpine`, 128 MB, named volume). The app
  is published only on `127.0.0.1:18090`, has `mem_limit 384m` and `no-new-privileges`, and joins the external
  network of docker-socket-proxy to reach it by name. Copy `deploy/.env.example` to `deploy/.env`, put
  `checks.yml` next to the compose file, then `docker compose -f deploy/vps-controller.compose.yml up -d`.
- `deploy/docker-socket-proxy.compose.yml` – the read-only proxy, on a fixed network named `docker-socket-proxy`.
- `deploy/checks.example.yml` – example checks.

## Security assumptions

- The controller never touches the Docker socket. It talks to
  [docker-socket-proxy](https://github.com/Tecnativa/docker-socket-proxy) with only `CONTAINERS=1`; every
  `POST`/`DELETE` and every other API section is refused by the proxy.
- The proxy listens on the server's loopback only (`deploy/docker-socket-proxy.compose.yml`); the laptop reaches it through an SSH tunnel.
- The backend binds to `127.0.0.1`; in the container it binds all interfaces but the published port stays
  loopback-only. It runs as a non-root user. No credentials live in this repo — the Telegram token, database
  password and heartbeat URL come from the environment only.
- V1 has no login because nothing is reachable from outside; authentication comes with the first deployment.

## Run locally

```bash
ssh -N -L 2375:127.0.0.1:23750 <server>          # tunnel to the proxy
docker run -d --name vpsc-db -e POSTGRES_DB=vpscontroller -e POSTGRES_USER=vpscontroller \
  -e POSTGRES_PASSWORD=dev -p 5432:5432 postgres:17.11-alpine
cd backend && ./mvnw spring-boot:run              # Java 25; DB_URL/VPS_DOCKER_URL/VPS_CHECKS_FILE override the defaults
cd frontend && npm ci && npm run dev              # http://localhost:5173
```

## Tests

- `backend`: `./mvnw verify` – 90 tests. V1: state table (20 cases), client error paths with
  `MockRestServiceServer`, `@WebMvcTest` for the 200 and the 503 contract, sorting. V2: state-machine table
  (`@CsvSource`), `accept`-range parsing, probe against a local `HttpServer` (200, 500, accepted 401, timeout,
  closed port), `CheckRunner` end-to-end against a local server + real Postgres with a fake clock, repository/
  uptime/bucket SQL with Testcontainers Postgres, `@WebMvcTest` for both new endpoints (200, 404, 400),
  Telegram notifier against a local `HttpServer` (message text, retry, no token in the log).
- `frontend`: `npm test` (Vitest) – the page never shows an empty "all fine" list when the API fails.
- CI: GitHub Actions runs both on every push (Testcontainers works on `ubuntu-latest`).

## Not (yet) in scope

Container control, own metrics storage, charts, Raspberry Pi, Hermes details, Prometheus.
