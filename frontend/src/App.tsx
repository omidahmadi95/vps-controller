import { useCallback, useEffect, useState } from 'react'
import { ChecksView } from './ChecksView'
import { ContainersView } from './ContainersView'
import { clock, countBy, loadChecks, loadContainers, loadHistory, CHECK_ORDER, CONTAINER_ORDER } from './load'
import { Readout } from './state'
import type { ChecksReport, ContainerReport, HistoryBucket, Load } from './types'

type Tab = 'dienste' | 'container'

export default function App() {
  const [tab, setTab] = useState<Tab>('dienste')
  const [checks, setChecks] = useState<Load<ChecksReport>>({ kind: 'loading' })
  const [containers, setContainers] = useState<Load<ContainerReport>>({ kind: 'loading' })
  const [history, setHistory] = useState<Record<number, HistoryBucket[]>>({})
  const [busy, setBusy] = useState(true)
  const [live, setLive] = useState('')

  const refreshChecks = useCallback(async () => {
    setBusy(true)
    const next = await loadChecks()
    setChecks(next)
    if (next.kind === 'ok') {
      const pairs = await Promise.all(next.report.checks.map(async (c) => [c.id, await loadHistory(c.id)] as const))
      setHistory(Object.fromEntries(pairs.filter((p) => p[1] !== null)) as Record<number, HistoryBucket[]>)
      setLive(`Aktualisiert ${clock(next.report.checkedAt)}`)
    }
    setBusy(false)
  }, [])

  const refreshContainers = useCallback(async () => {
    setBusy(true)
    const next = await loadContainers()
    setContainers(next)
    if (next.kind === 'ok') setLive(`Aktualisiert ${clock(next.report.checkedAt)}`)
    setBusy(false)
  }, [])

  const refresh = tab === 'dienste' ? refreshChecks : refreshContainers
  useEffect(() => {
    void refresh()
  }, [refresh])
  useEffect(() => {
    const id = setInterval(() => {
      if (tab === 'dienste') void refreshChecks()
    }, 30_000)
    return () => clearInterval(id)
  }, [tab, refreshChecks])

  const counts = tab === 'dienste'
    ? (checks.kind === 'ok' ? countBy(checks.report.checks, CHECK_ORDER) : null)
    : (containers.kind === 'ok' ? countBy(containers.report.containers, CONTAINER_ORDER) : null)
  const checkedAt = tab === 'dienste'
    ? (checks.kind === 'ok' ? checks.report.checkedAt : null)
    : (containers.kind === 'ok' ? containers.report.checkedAt : null)

  return (
    <>
      <header className="top">
        <div className="wrap">
          <div className="bar">
            <h1 className="brand">VPS Controller</h1>
            <nav className="tabs" aria-label="Ansicht">
              <button type="button" aria-pressed={tab === 'dienste'} onClick={() => setTab('dienste')}>Dienste</button>
              <button type="button" aria-pressed={tab === 'container'} onClick={() => setTab('container')}>Container</button>
            </nav>
            <button type="button" className="reload" onClick={() => void refresh()} disabled={busy} aria-busy={busy}>
              {busy ? 'Lädt …' : 'Neu laden'}
            </button>
          </div>
          <Readout counts={counts} checkedAt={checkedAt} />
        </div>
      </header>
      <main className="wrap" aria-busy={busy}>
        {tab === 'dienste' ? <ChecksView load={checks} history={history} /> : <ContainersView load={containers} />}
      </main>
      <p className="sr-only" role="status" aria-live="polite">{live}</p>
    </>
  )
}
