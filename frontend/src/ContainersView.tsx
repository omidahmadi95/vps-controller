import type { ContainerReport, Load } from './types'
import { Alert, Skeleton, StateChip } from './state'

const COLS = ['Zustand', 'Container', 'Detail', 'Image']

export function ContainersView({ load }: { load: Load<ContainerReport> }) {
  if (load.kind === 'loading') return <Skeleton cols={COLS} />
  if (load.kind === 'unreachable') return <Alert detail={load.detail} checkedAt={load.checkedAt} />
  const { containers } = load.report
  if (containers.length === 0) return <p className="empty">Keine Container</p>
  return (
    <div className="scroll">
      <table>
        <thead>
          <tr>
            <th>Zustand</th>
            <th>Container</th>
            <th className="col-detail">Detail</th>
            <th className="col-image">Image</th>
          </tr>
        </thead>
        <tbody>
          {containers.map((c) => (
            <tr key={c.name} className={c.state === 'DEGRADED' || c.state === 'UNREACHABLE' ? 'row-bad' : ''}>
              <td><StateChip state={c.state} label={c.label} /></td>
              <td><span className="title">{c.name}</span></td>
              <td className="col-detail">{c.detail}</td>
              <td className="col-image"><code className="host">{c.image}</code></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
