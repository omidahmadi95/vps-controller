export type ContainerState = 'RUNNING' | 'DEGRADED' | 'STOPPED' | 'UNREACHABLE' | 'UNKNOWN'

export interface ContainerView {
  name: string
  image: string
  state: ContainerState
  label: string
  detail: string
}

export interface ContainerReport {
  checkedAt: string
  containers: ContainerView[]
}

export type CheckState = 'UP' | 'DOWN' | 'PENDING' | 'PAUSED'

export interface CheckView {
  id: number
  name: string
  url: string
  state: CheckState
  label: string
  since: string | null
  lastCheckedAt: string | null
  lastStatusCode: number | null
  lastLatencyMs: number | null
  lastError: string | null
  uptime24h: number | null
  uptime30d: number | null
}

export interface ChecksReport {
  checkedAt: string
  checks: CheckView[]
}

export interface HistoryBucket {
  start: string
  total: number
  failed: number
  avgLatencyMs: number | null
}

export interface CheckHistory {
  checkId: number
  from: string
  to: string
  buckets: HistoryBucket[]
}

/** What a view shows: a report, or the reason there is none. Never a silent empty list. */
export type Load<T> =
  | { kind: 'loading' }
  | { kind: 'ok'; report: T }
  | { kind: 'unreachable'; detail: string; checkedAt: string }

export type OverviewStatus = 'PROBLEM' | 'UNCERTAIN' | 'CALM'
export type IncidentKind = 'CHECK' | 'CONTAINER' | 'SOURCE'
export type IncidentSeverity = 'PROBLEM' | 'UNCERTAIN'
export type SourceName = 'checks' | 'containers' | 'heartbeat'
export type SourceState = 'OK' | 'STALE' | 'UNREACHABLE' | 'NOT_CONFIGURED'

export interface OverviewCounts {
  problem: number
  uncertain: number
  ok: number
  stopped: number
  paused: number
}

export interface OverviewIncident {
  kind: IncidentKind
  severity: IncidentSeverity
  name: string
  /** CheckState or ContainerState for CHECK/CONTAINER, the source state for SOURCE. */
  state: CheckState | ContainerState | SourceState
  label: string
  since: string | null
  detail: string | null
  checkId: number | null
}

export interface OverviewSource {
  name: SourceName
  state: SourceState
  label: string
  lastSeenAt: string | null
}

export interface RecentOutage {
  checkId: number
  name: string
  start: string
  end: string | null
  durationSeconds: number
  failures: number
}

export interface OverviewReport {
  checkedAt: string
  status: OverviewStatus
  counts: OverviewCounts
  incidents: OverviewIncident[]
  sources: OverviewSource[]
  recentOutages: RecentOutage[]
}
