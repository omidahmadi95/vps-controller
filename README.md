# VPS Controller

A small dashboard that answers one question about my server: **which containers are not fine right now?**

Spring Boot reads the container list from the Docker Engine API, turns Docker's raw state and free-text
status into one unambiguous state, and a React page lists the problems first.

| State | Meaning |
|---|---|
| **Läuft** | running, and either healthy or without a health check |
| **Gestört** | unhealthy, restart loop, crashed (exit ≠ 0) or dead |
| **Gestoppt** | exited cleanly (exit 0), paused or never started |
| **Unbekannt** | health check still starting, unknown Docker state, missing exit code |
| **Nicht erreichbar** | the Docker source did not answer usably — the API returns 503, never an empty list |

Rule: only a container that is *proven* to run is shown as «Läuft». Anything uncertain is something else.

## Architecture (V1)

```
React (Vite, TS) ──/api──▶ Spring Boot 4 (Java 25) ──HTTP GET──▶ docker-socket-proxy ──▶ Docker
   localhost:5173            127.0.0.1:8080              SSH tunnel            (read-only: CONTAINERS=1, POST=0)
```

- `ContainerStatusMapper` – the state rules above (parses health and exit code out of Docker's `Status` text).
- `DockerClient` – `GET /containers/json?all=true`; connection errors, timeouts (3 s), HTTP errors and
  unreadable JSON all become `SourceUnreachableException`.
- `ContainerService` – maps and sorts by urgency (Gestört → Nicht erreichbar → Unbekannt → Gestoppt → Läuft), then by name.
- `ContainerController` – `GET /api/containers`; `ApiErrors` turns an unreachable source into an RFC 9457
  problem (`503`, `title: "Nicht erreichbar"`).

## API

`GET /api/containers`

```json
{ "checkedAt": "2026-11-22T20:00:00Z",
  "containers": [ { "name": "api", "image": "app:1", "state": "DEGRADED", "label": "Gestört",
                    "detail": "Health-Check schlägt fehl" } ] }
```

Source unreachable → `503 application/problem+json`:
`{ "title": "Nicht erreichbar", "status": 503, "detail": "…", "state": "UNREACHABLE", "checkedAt": "…" }`

## Security assumptions

- The controller never touches the Docker socket. It talks to
  [docker-socket-proxy](https://github.com/Tecnativa/docker-socket-proxy) with only `CONTAINERS=1`; every
  `POST`/`DELETE` and every other API section is refused by the proxy.
- The proxy listens on the server's loopback only (`deploy/docker-socket-proxy.compose.yml`); the laptop reaches it through an SSH tunnel.
- The backend binds to `127.0.0.1`, runs as a normal user and stores nothing. No credentials live in this repo.
- V1 has no login because nothing is reachable from outside; authentication comes with the first deployment.

## Run locally

```bash
ssh -N -L 2375:127.0.0.1:23750 <server>          # tunnel to the proxy
cd backend && ./mvnw spring-boot:run              # Java 25; VPS_DOCKER_URL overrides http://127.0.0.1:2375
cd frontend && npm ci && npm run dev              # http://localhost:5173
```

## Tests

- `backend`: `./mvnw verify` – state table (20 cases), client error paths with `MockRestServiceServer`
  (5xx, connection refused, broken JSON), `@WebMvcTest` for the 200 and the 503 contract, sorting.
- `frontend`: `npm test` (Vitest) – the page never shows an empty "all fine" list when the API fails.
- CI: GitHub Actions runs both on every push.

## Not in V1 (on purpose)

Container control, own metrics storage, charts, notifications, Raspberry Pi, Hermes details, Prometheus.
