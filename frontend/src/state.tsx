import { CircleCheck, CircleHelp, CirclePause, OctagonAlert, Unplug, type LucideIcon } from 'lucide-react'
import type { CheckState, ContainerState } from './types'
import { clock } from './load'

type Tone = 'ok' | 'bad' | 'warn' | 'muted'
type AnyState = CheckState | ContainerState

// State = shape icon + colour + text, so it reads without colour.
const STATE: Record<AnyState, { tone: Tone; icon: LucideIcon }> = {
  UP: { tone: 'ok', icon: CircleCheck },
  RUNNING: { tone: 'ok', icon: CircleCheck },
  DOWN: { tone: 'bad', icon: OctagonAlert },
  DEGRADED: { tone: 'bad', icon: OctagonAlert },
  UNREACHABLE: { tone: 'bad', icon: Unplug },
  PENDING: { tone: 'warn', icon: CircleHelp },
  UNKNOWN: { tone: 'warn', icon: CircleHelp },
  PAUSED: { tone: 'muted', icon: CirclePause },
  STOPPED: { tone: 'muted', icon: CirclePause },
}

export function StateChip({ state, label }: { state: AnyState; label: string }) {
  const { tone, icon: Icon } = STATE[state]
  return (
    <span className={`chip ${tone}`}>
      <Icon size={16} strokeWidth={1.5} aria-hidden="true" />
      {label}
    </span>
  )
}

/** Header readout: counts in urgency order + the check time. Null when there is no report. */
export function Readout({ counts, checkedAt }: { counts: [string, number][] | null; checkedAt: string | null }) {
  if (!counts || counts.length === 0 || !checkedAt) return null
  return (
    <div className="readout">
      <span className="counts">
        {counts.map(([label, n]) => (
          <span className="count" key={label}>
            <b>{n}</b> {label}
          </span>
        ))}
      </span>
      <span className="checked">geprüft {clock(checkedAt)}</span>
    </div>
  )
}

/** Error state: alert with the reason and when the attempt happened. */
export function Alert({ detail, checkedAt }: { detail: string; checkedAt: string }) {
  return (
    <p role="alert" className="alert">
      <Unplug size={18} strokeWidth={1.5} aria-hidden="true" />
      Nicht erreichbar: {detail}
      <time>geprüft {clock(checkedAt)}</time>
    </p>
  )
}

/** Loading state: skeleton rows, never a "Lade …" text. */
export function Skeleton({ cols }: { cols: string[] }) {
  return (
    <div className="scroll">
      <table aria-hidden="true">
        <thead>
          <tr>{cols.map((c) => <th key={c}>{c}</th>)}</tr>
        </thead>
        <tbody>
          {Array.from({ length: 5 }, (_, row) => (
            <tr key={row}>
              {cols.map((c) => <td key={c}><span className="skel" /></td>)}
            </tr>
          ))}
        </tbody>
      </table>
      <span className="sr-only">Wird geladen</span>
    </div>
  )
}
