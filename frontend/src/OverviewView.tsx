import { CircleCheck, CircleHelp, CirclePause, OctagonAlert, Unplug, type LucideIcon } from 'lucide-react'
import type { IncidentSeverity, Load, OverviewReport, SourceState } from './types'
import { duration, sinceDuration, sinceText, verdictText } from './load'
import { Alert } from './state'

// Severity = shape icon + colour + the incident's own German label: readable without colour.
const SEVERITY: Record<IncidentSeverity, { tone: 'bad' | 'warn'; Icon: LucideIcon }> = {
  PROBLEM: { tone: 'bad', Icon: OctagonAlert },
  UNCERTAIN: { tone: 'warn', Icon: CircleHelp },
}

const SOURCE: Record<SourceState, { tone: 'ok' | 'warn' | 'bad' | 'muted'; Icon: LucideIcon }> = {
  OK: { tone: 'ok', Icon: CircleCheck },
  STALE: { tone: 'warn', Icon: CircleHelp },
  UNREACHABLE: { tone: 'bad', Icon: Unplug },
  NOT_CONFIGURED: { tone: 'muted', Icon: CirclePause },
}

const SOURCE_NAME: Record<string, string> = { checks: 'Dienstprüfung', containers: 'Container', heartbeat: 'Totmann extern' }

export function OverviewView({ load, onShowDienste }: { load: Load<OverviewReport>; onShowDienste: () => void }) {
  if (load.kind === 'loading') {
    return (
      <div>
        <div className="cards" aria-hidden="true">
          {[0, 1].map((i) => (
            <div className="inc" key={i}>
              <div className="body"><span className="skel" /><span className="skel" /></div>
            </div>
          ))}
        </div>
        <span className="sr-only">Wird geladen</span>
      </div>
    )
  }
  if (load.kind === 'unreachable') return <Alert detail={load.detail} checkedAt={load.checkedAt} />

  const { checkedAt, status, counts, incidents, sources, recentOutages } = load.report
  return (
    <div className="overview">
      <div className={`verdict ${status.toLowerCase()}`}>
        <h2>{verdictText(status, counts)}</h2>
      </div>

      {incidents.length > 0 && (
        <div className="cards">
          {incidents.map((inc) => {
            const { tone, Icon } = SEVERITY[inc.severity]
            return (
              <article className={`inc ${tone}`} key={`${inc.kind}-${inc.name}`}>
                <div className="head">
                  <span className="ico" aria-hidden="true"><Icon size={16} strokeWidth={1.5} /></span>
                  <span className="t">{inc.name}</span>
                  {inc.since && <span className="since">seit {sinceDuration(inc.since, checkedAt)}</span>}
                </div>
                <div className="body">
                  <span className={`chip ${tone}`}><Icon size={16} strokeWidth={1.5} aria-hidden="true" />{inc.label}</span>
                  {inc.detail && <p className="detail">{inc.detail}</p>}
                  {inc.kind === 'CHECK' && (
                    <div className="actions">
                      <button type="button" className="go" onClick={onShowDienste}>Zum Dienst</button>
                    </div>
                  )}
                </div>
              </article>
            )
          })}
        </div>
      )}

      <div className="sources">
        {sources.map((s) => {
          const { tone, Icon } = SOURCE[s.state]
          return (
            <span className={`src ${tone}`} key={s.name}>
              <Icon size={16} strokeWidth={1.5} aria-hidden="true" />
              <span className="k">{SOURCE_NAME[s.name] ?? s.name}</span>
              <b className="v">{s.label}</b>
            </span>
          )
        })}
      </div>

      <h2 className="sec">Letzte sieben Tage</h2>
      {recentOutages.length === 0 ? (
        <p className="empty">Keine Unterbrechungen in den letzten sieben Tagen</p>
      ) : (
        <div className="scroll">
          <table>
            <thead>
              <tr><th>Dienst</th><th>Beginn</th><th className="num">Dauer</th></tr>
            </thead>
            <tbody>
              {recentOutages.map((o) => (
                <tr key={`${o.checkId}-${o.start}`}>
                  <td>{o.name}</td>
                  <td>{sinceText(o.start)}</td>
                  <td className="num">{duration(o.durationSeconds)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
