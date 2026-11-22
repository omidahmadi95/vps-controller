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
