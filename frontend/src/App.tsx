import { useEffect, useState } from 'react'
import { loadContainers } from './load'
import type { ContainerState, Load } from './types'

const tone: Record<ContainerState, string> = {
  RUNNING: 'ok',
  DEGRADED: 'bad',
  STOPPED: 'muted',
  UNREACHABLE: 'bad',
  UNKNOWN: 'warn',
}

const time = (iso: string) => new Date(iso).toLocaleTimeString('de-CH')

export function ContainerTable({ load }: { load: Load }) {
  if (load.kind === 'loading') return <p>Lade …</p>
  if (load.kind === 'unreachable')
    return (
      <p role="alert" className="badge bad">
        Nicht erreichbar: {load.detail} (geprüft {time(load.checkedAt)})
      </p>
    )
  const { containers, checkedAt } = load.report
  // "2 Gestört · 11 Gestoppt · 48 Läuft": intentionally stopped containers are not counted as problems.
  const counts = new Map<string, number>()
  for (const c of containers) counts.set(c.label, (counts.get(c.label) ?? 0) + 1)
  const summary = [...counts].map(([label, n]) => `${n} ${label}`).join(' · ')
  return (
    <>
      <p>
        {containers.length} Container: {summary} · geprüft {time(checkedAt)}
      </p>
      <table>
        <thead>
          <tr><th>Container</th><th>Status</th><th>Detail</th><th>Image</th></tr>
        </thead>
        <tbody>
          {containers.map((c) => (
            <tr key={c.name}>
              <td>{c.name}</td>
              <td><span className={`badge ${tone[c.state]}`}>{c.label}</span></td>
              <td>{c.detail}</td>
              <td><code>{c.image}</code></td>
            </tr>
          ))}
        </tbody>
      </table>
    </>
  )
}

export default function App() {
  const [load, setLoad] = useState<Load>({ kind: 'loading' })
  const refresh = () => loadContainers().then(setLoad)
  useEffect(() => { refresh() }, [])
  return (
    <main>
      <header>
        <h1>VPS Controller</h1>
        <button type="button" onClick={refresh}>Neu laden</button>
      </header>
      <ContainerTable load={load} />
    </main>
  )
}
