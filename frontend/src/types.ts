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

/** What the page shows: a report, or the reason there is none. Never a silent empty list. */
export type Load =
  | { kind: 'loading' }
  | { kind: 'ok'; report: ContainerReport }
  | { kind: 'unreachable'; detail: string; checkedAt: string }
