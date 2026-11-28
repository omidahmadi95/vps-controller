# VPS Controller Roadmap

Only one phase is active at a time. A phase is complete only when its acceptance criteria are verified against the repository and, where applicable, the live system.

## Phase 0 — Prove monitoring reliability (complete, 2026-11-28)

### Result of the parallel run (2026-11-23 → 2026-11-28)

The owner accepted the evidence after 5.5 days instead of the planned two weeks: the previous monitor had no
notification channel at all, so switching it off removed no alerting.

- Nine real checks, 60 s interval: 7,906 of 7,914 expected results each (99.9 %); the remainder is explained by
  ~60 ms scheduling drift per run. No gap longer than three intervals, no controller or database restart.
- Real outages were detected and debounced as designed: a client health endpoint failed twice in a row
  (threshold 2) and produced one down and one recovery notification; isolated single failures (a resolver hiccup
  hitting three checks in the same second, single timeouts) produced none. No failed Telegram delivery and no
  failed heartbeat in the logs.
- The previous monitor agreed on every service both watched (no downs), had **no notification channel**, and
  reported 8 of its 13 monitors at 0 % for the whole window without anyone being told — retired or unreachable
  services, plus one site behind Basic Auth that the controller correctly accepts as `401`.
- Cut-over: the previous monitor was stopped, its data volume archived and checksum-verified (SQLite
  `quick_check` ok), and a restore by restart was tested once (reachable again after 9 s), then stopped again.
  Volume and archive are retained; removal needs a separate approval.

### Evidence collected before the parallel run

- HTTP checks, PostgreSQL history and Telegram notifications are deployed.
- The external dead-man check is configured with a two-minute period and three-minute grace time.
- A controlled controller stop produced an external `UP → DOWN → UP` transition; heartbeat delivery and recovery are verified.
- A synthetic HTTP check produced `UP → DOWN → UP` through the probe, state machine, PostgreSQL, API and Telegram; the retained test check is now `PAUSED`.
- Controlled application, PostgreSQL and full Compose stop/start tests retained the database volume and all 777 existing probe results. The full stack recovered in 22 seconds, monitoring resumed with new results, both APIs recovered and all nine real checks returned `UP`.
- The two-week parallel observation window started on 2026-11-23; Uptime Kuma remains active until it completes.

### Work

1. Configure an external dead-man check without committing its secret URL.
2. Verify heartbeat arrival and a missed-heartbeat notification.
3. Run a synthetic outage and recovery through probe, state machine, PostgreSQL, API, UI and Telegram.
4. Verify controller, database and Compose restart recovery.
5. Observe the controller beside Uptime Kuma for about two weeks.
6. Back up and stop Uptime Kuma only after the evidence is accepted; retain a rollback path.

### Acceptance

- The external service receives regular heartbeats and alerts when they stop beyond its grace period.
- One controlled check produces exactly one down alert after its configured threshold and one recovery alert after recovery.
- API, UI, stored history and Telegram agree on the transition and duration.
- Restart tests retain configuration and history.
- All real checks return to their expected state after testing.
- The parallel-run record has no unexplained missed checks, restarts or alert discrepancies.
- Uptime Kuma is not removed before explicit approval after the observation period.

## Phase 1 — Problem-first overview (active)

Create a mobile-first overview of active service incidents, degraded containers and data freshness. Do not add synthetic metrics or decorative charts.

## Phase 2 — Selected Prometheus metrics (planned)

Add CPU, memory, disk and system-load time series through the Prometheus HTTP API. Keep Grafana for deeper analysis.

## Phase 3 — Expected-versus-actual drift (planned)

Read the server inventory without write access and compare expected services with observed Docker state in tested Java domain logic.

## Phase 4 — Incident timeline (planned)

Persist and explain state transitions, outage duration and relevant technical context without building a ticketing system.

## Phase 5 — Private device access (planned)

Provide authenticated private-network access for trusted devices while keeping the application off the public internet.

## Phase 6 — Portfolio evidence (planned)

Publish an architecture explanation, verified engineering decisions and synthetic screenshots or a synthetic static demo. Never connect a public demo to the real VPS.
