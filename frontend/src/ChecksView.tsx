import type { ChecksReport, HistoryBucket, Load } from './types'
import { bucketClass, clock, hostOf, pct, sinceText } from './load'
import { Alert, Skeleton, StateChip } from './state'

const COLS = ['Zustand', 'Name', 'Verfügbarkeit 24 h', '30 d', 'Antwortzeit', 'Seit', '24 h']

function Strip({ buckets }: { buckets?: HistoryBucket[] }) {
  if (!buckets) return <span className="strip-none">—</span>
  return (
    <span className="strip" role="img" aria-label="Verlauf der letzten 24 Stunden">
      {buckets.map((b) => (
        <span
          key={b.start}
          className={`tick ${bucketClass(b)}`}
          title={`${clock(b.start)} · ${b.total === 0 ? 'keine Prüfungen' : `${b.total} Prüfungen, ${b.failed} fehlgeschlagen`}`}
        />
      ))}
    </span>
  )
}

export function ChecksView({ load, history }: { load: Load<ChecksReport>; history: Record<number, HistoryBucket[]> }) {
  if (load.kind === 'loading') return <Skeleton cols={COLS} />
  if (load.kind === 'unreachable') return <Alert detail={load.detail} checkedAt={load.checkedAt} />
  const { checks } = load.report
  if (checks.length === 0) return <p className="empty">Keine Dienste konfiguriert</p>
  return (
    <div className="scroll">
      <table>
        <thead>
          <tr>
            <th>Zustand</th>
            <th>Name</th>
            <th className="num col-uptime">Verfügbarkeit 24 h</th>
            <th className="num col-30">30 d</th>
            <th className="num col-latency">Antwortzeit</th>
            <th className="col-since">Seit</th>
            <th className="col-strip">24 h</th>
          </tr>
        </thead>
        <tbody>
          {checks.map((c) => (
            <tr key={c.id} className={c.state === 'DOWN' ? 'row-bad' : ''}>
              <td><StateChip state={c.state} label={c.label} /></td>
              <td>
                <span className="name">
                  <span className="title" title={c.url}>{c.name}</span>
                  <span className="host col-host">{hostOf(c.url)}</span>
                  {c.lastError && <span className="err">{c.lastError}</span>}
                </span>
              </td>
              <td className="num col-uptime">{pct(c.uptime24h)}</td>
              <td className="num col-30">{pct(c.uptime30d)}</td>
              <td className="num col-latency">{c.lastLatencyMs == null ? '—' : `${c.lastLatencyMs} ms`}</td>
              <td className="col-since">{c.since ? sinceText(c.since) : '—'}</td>
              <td className="col-strip"><Strip buckets={history[c.id]} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
