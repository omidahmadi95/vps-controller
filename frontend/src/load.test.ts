import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { createElement } from 'react'
import { loadContainers } from './load'
import { ContainerTable } from './App'

const respond = (status: number, body: unknown) =>
  (async () => new Response(typeof body === 'string' ? body : JSON.stringify(body), { status })) as typeof fetch

describe('loadContainers', () => {
  it('returns the report on 200', async () => {
    const load = await loadContainers(respond(200, { checkedAt: '2026-11-22T20:00:00Z', containers: [] }))
    expect(load.kind).toBe('ok')
  })

  it('503 problem detail is unreachable, never an empty ok list', async () => {
    const load = await loadContainers(respond(503, { title: 'Nicht erreichbar', detail: 'refused', checkedAt: '2026-11-22T20:00:00Z' }))
    expect(load).toMatchObject({ kind: 'unreachable', detail: 'refused' })
  })

  it('non-JSON answer is unreachable', async () => {
    expect((await loadContainers(respond(200, '<html>'))).kind).toBe('unreachable')
  })

  it('network error is unreachable', async () => {
    const fail = (async () => { throw new TypeError('fetch failed') }) as typeof fetch
    expect(await loadContainers(fail)).toMatchObject({ kind: 'unreachable', detail: 'Backend nicht erreichbar' })
  })
})

describe('ContainerTable', () => {
  it('shows the German label and counts problems', () => {
    const html = renderToStaticMarkup(createElement(ContainerTable, { load: { kind: 'ok', report: {
      checkedAt: '2026-11-22T20:00:00Z',
      containers: [
        { name: 'api', image: 'app:1', state: 'DEGRADED', label: 'Gestört', detail: 'Health-Check schlägt fehl' },
        { name: 'web', image: 'nginx', state: 'RUNNING', label: 'Läuft', detail: 'Kein Health-Check definiert' },
      ] } } }))
    expect(html).toContain('Gestört')
    expect(html).toContain('2 Container, 1 nicht «Läuft»')
  })
})
