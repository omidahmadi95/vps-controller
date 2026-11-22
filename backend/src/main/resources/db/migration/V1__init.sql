create table http_check (
    id                 bigint generated always as identity primary key,
    name               text not null unique,
    url                text not null,
    interval_seconds   int  not null,
    timeout_seconds    int  not null,
    accept             text not null,
    failure_threshold  int  not null,
    recovery_threshold int  not null,
    enabled            boolean not null default true
);

-- Current state machine position per check.
create table check_state (
    check_id              bigint primary key references http_check (id) on delete cascade,
    state                 text not null,
    consecutive_failures  int not null default 0,
    consecutive_successes int not null default 0,
    since                 timestamptz
);

-- One probe result per run; retention deletes rows older than 30 days.
create table check_result (
    id          bigint generated always as identity primary key,
    check_id    bigint not null references http_check (id) on delete cascade,
    checked_at  timestamptz not null,
    ok          boolean not null,
    status_code int,
    latency_ms  int,
    error       text
);

create index check_result_check_time on check_result (check_id, checked_at desc);
