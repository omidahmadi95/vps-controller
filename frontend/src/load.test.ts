import { describe, expect, it } from 'vitest'
import { renderToStaticMarkup } from 'react-dom/server'
import { createElement } from 'react'
import { bucketClass, countBy, duration, hostOf, loadChecks, loadContainers, loadHistory, loadOverview, overviewCounts, pct, sinceText, verdictText } from './load'
import { Readout } from './state'
import { ChecksView } from './ChecksView'
import { ContainersView } from './ContainersView'
import { OverviewView } from './OverviewView'
import type { ChecksReport, ContainerReport, HistoryBucket, Load, OverviewReport, OverviewSource } from './types'

const respond = (status: number, body: unknown) =>
  (async () => new Response(typeof body === 'string' ? body : JSON.stringify(body), { status })) as typeof fetch
const fail = (async () => {
  throw new TypeError('fetch failed')
}) as typeof fetch

const checksReport: ChecksReport = {
  checkedAt: '2026-11-23T10:00:00Z',
  checks: [
    { id: 3, name: 'Shop', url: 'https://shop.example.org/health', state: 'DOWN', label: 'Ausgefallen', since: '2026-11-23T09:41:00Z', lastCheckedAt: '2026-11-23T09:59:30Z', lastStatusCode: 525, lastLatencyMs: 92, lastError: 'HTTP 525 (erwartet 200-399)', uptime24h: 0.0, uptime30d: 61.3 },
    { id: 4, name: 'API', url: 'https://api.example.org/health', state: 'UP', label: 'Erreichbar', since: '2026-11-22T10:00:00Z', lastCheckedAt: '2026-11-23T09:59:40Z', lastStatusCode: 200, lastLatencyMs: 41, lastError: null, uptime24h: 99.9, uptime30d: 100.0 },
  ],
}
const containersReport: ContainerReport = {
  checkedAt: '2026-11-22T20:00:00Z',
  containers: [
    { name: 'api', image: 'app:1', state: 'DEGRADED', label: 'Gestört', detail: 'Health-Check schlägt fehl' },
    { name: 'web', image: 'nginx', state: 'RUNNING', label: 'Läuft', detail: 'Kein Health-Check definiert' },
  ],
}
const buckets = (n: number): HistoryBucket[] =>
  Array.from({ length: n }, (_, i) => ({ start: `2026-11-22T${String(i % 24).padStart(2, '0')}:00:00Z`, total: i === 1 ? 0 : 30, failed: i === 2 ? 30 : 0, avgLatencyMs: i === 1 ? null : 81 }))

describe('loadContainers', () => {
  it('returns the report on 200', async () => {
    expect((await loadContainers(respond(200, { checkedAt: 'x', containers: [] }))).kind).toBe('ok')
  })
  it('503 problem detail is unreachable, never an empty ok list', async () => {
    const load = await loadContainers(respond(503, { title: 'Nicht erreichbar', detail: 'refused', checkedAt: '2026-11-22T20:00:00Z' }))
    expect(load).toMatchObject({ kind: 'unreachable', detail: 'refused' })
  })
  it('non-JSON answer is unreachable', async () => {
    expect((await loadContainers(respond(200, '<html>'))).kind).toBe('unreachable')
  })
  it('network error is unreachable', async () => {
    expect(await loadContainers(fail)).toMatchObject({ kind: 'unreachable', detail: 'Backend nicht erreichbar' })
  })
})

describe('loadChecks', () => {
  it('returns the report on 200', async () => {
    expect(await loadChecks(respond(200, checksReport))).toMatchObject({ kind: 'ok' })
  })
  it('503 detail becomes unreachable', async () => {
    expect(await loadChecks(respond(503, { detail: 'keine Verbindung zu Postgres' })))
      .toMatchObject({ kind: 'unreachable', detail: 'keine Verbindung zu Postgres' })
  })
  it('500 is unreachable with the status text', async () => {
    expect(await loadChecks(respond(500, 'boom'))).toMatchObject({ kind: 'unreachable', detail: 'API antwortet mit 500' })
  })
  it('a 200 without a checks array is unreachable', async () => {
    expect((await loadChecks(respond(200, { foo: 1 }))).kind).toBe('unreachable')
  })
  it('non-JSON is unreachable', async () => {
    expect((await loadChecks(respond(200, '<html>'))).kind).toBe('unreachable')
  })
  it('network error is unreachable', async () => {
    expect(await loadChecks(fail)).toMatchObject({ kind: 'unreachable', detail: 'Backend nicht erreichbar' })
  })
})

describe('loadHistory', () => {
  it('returns the buckets on 200', async () => {
    const b = await loadHistory(3, respond(200, { checkId: 3, from: 'a', to: 'b', buckets: buckets(48) }))
    expect(b).toHaveLength(48)
  })
  it('404 is null, not a broken page state', async () => {
    expect(await loadHistory(9, respond(404, { detail: 'nope' }))).toBeNull()
  })
  it('non-JSON is null and network error is null', async () => {
    expect(await loadHistory(3, respond(200, '<html>'))).toBeNull()
    expect(await loadHistory(3, fail)).toBeNull()
  })
})

describe('bucketClass', () => {
  it('maps a bucket to its bar colour', () => {
    expect(bucketClass({ start: 'a', total: 0, failed: 0, avgLatencyMs: null })).toBe('strip-empty')
    expect(bucketClass({ start: 'a', total: 30, failed: 30, avgLatencyMs: 5 })).toBe('strip-bad')
    expect(bucketClass({ start: 'a', total: 30, failed: 0, avgLatencyMs: 5 })).toBe('strip-ok')
  })
})

describe('countBy', () => {
  it('counts per label in urgency order', () => {
    const items = [{ label: 'Erreichbar' }, { label: 'Ausgefallen' }, { label: 'Erreichbar' }]
    expect(countBy(items, ['Ausgefallen', 'Erreichbar'])).toEqual([['Ausgefallen', 1], ['Erreichbar', 2]])
  })
  it('puts unknown labels last', () => {
    expect(countBy([{ label: 'Neu' }, { label: 'Ausgefallen' }], ['Ausgefallen'])[1]).toEqual(['Neu', 1])
  })
})

describe('formatting helpers', () => {
  it('formats percentages with one decimal or a dash', () => {
    expect(pct(61.3)).toBe('61,3 %')
    expect(pct(0)).toBe('0,0 %')
    expect(pct(null)).toBe('—')
  })
  it('extracts the host and survives a broken URL', () => {
    expect(hostOf('https://shop.example.org/health')).toBe('shop.example.org')
    expect(hostOf('nope')).toBe('nope')
  })
  it('shows a date for a state that began before today', () => {
    expect(sinceText('2026-11-22T09:41:00Z', new Date('2026-11-23T10:00:00Z'))).toContain('22.11.')
  })
})

describe('Readout', () => {
  it('shows no counts without a report — never an "all fine" row', () => {
    expect(renderToStaticMarkup(createElement(Readout, { counts: null, checkedAt: null }))).toBe('')
  })
  it('shows the counts and the check time', () => {
    const html = renderToStaticMarkup(createElement(Readout, { counts: countBy(checksReport.checks, ['Ausgefallen', 'Erreichbar']), checkedAt: checksReport.checkedAt }))
    expect(html).toContain('Ausgefallen')
    expect(html).toContain('geprüft')
  })
})

describe('ChecksView', () => {
  it('renders the label, host and 48-bar strip', () => {
    const html = renderToStaticMarkup(createElement(ChecksView, { load: { kind: 'ok', report: checksReport }, history: { 3: buckets(48) } }))
    expect(html).toContain('Ausgefallen')
    expect(html).toContain('shop.example.org')
    expect(html.split('class="tick ').length - 1).toBe(48)
  })
  it('loading shows skeleton rows, not "Lade …"', () => {
    const html = renderToStaticMarkup(createElement(ChecksView, { load: { kind: 'loading' } as Load<ChecksReport>, history: {} }))
    expect(html).toContain('skel')
    expect(html).not.toContain('Lade')
  })
  it('empty is explicit', () => {
    const html = renderToStaticMarkup(createElement(ChecksView, { load: { kind: 'ok', report: { checkedAt: 'x', checks: [] } }, history: {} }))
    expect(html).toContain('Keine Dienste konfiguriert')
  })
  it('a failed API is an alert, not an all-fine state', () => {
    const html = renderToStaticMarkup(createElement(ChecksView, { load: { kind: 'unreachable', detail: 'refused', checkedAt: '2026-11-23T10:00:00Z' }, history: {} }))
    expect(html).toContain('role="alert"')
    expect(html).toContain('refused')
    expect(html).not.toContain('Erreichbar')
  })
})

describe('ContainersView', () => {
  it('shows the German label per container', () => {
    const html = renderToStaticMarkup(createElement(ContainersView, { load: { kind: 'ok', report: containersReport } }))
    expect(html).toContain('Gestört')
    expect(html).toContain('Läuft')
  })
  it('empty is explicit', () => {
    const html = renderToStaticMarkup(createElement(ContainersView, { load: { kind: 'ok', report: { checkedAt: 'x', containers: [] } } }))
    expect(html).toContain('Keine Container')
  })
})

const overviewReport: OverviewReport = {
  checkedAt: '2026-11-28T07:12:04Z',
  status: 'PROBLEM',
  counts: { problem: 1, uncertain: 0, ok: 11, stopped: 2, paused: 1 },
  incidents: [
    { kind: 'CHECK', severity: 'PROBLEM', name: 'api.example.org', state: 'DOWN', label: 'Ausgefallen', since: '2026-11-28T07:08:31Z', detail: 'HTTP 503 (erwartet 200-399)', checkId: 7 },
  ],
  sources: [
    { name: 'checks', state: 'OK', label: 'Aktuell', lastSeenAt: '2026-11-28T07:11:58Z' },
    { name: 'containers', state: 'OK', label: 'Aktuell', lastSeenAt: '2026-11-28T07:12:04Z' },
    { name: 'heartbeat', state: 'NOT_CONFIGURED', label: 'Nicht eingerichtet', lastSeenAt: null },
  ],
  recentOutages: [
    { checkId: 3, name: 'shop.example.org', start: '2026-11-27T04:33:54Z', end: '2026-11-27T04:35:55Z', durationSeconds: 121, failures: 2 },
  ],
}
const calmReport: OverviewReport = {
  ...overviewReport,
  status: 'CALM',
  counts: { problem: 0, uncertain: 0, ok: 12, stopped: 2, paused: 1 },
  incidents: [],
  recentOutages: [],
}

describe('loadOverview', () => {
  it('returns the report on 200', async () => {
    expect(await loadOverview(respond(200, overviewReport))).toMatchObject({ kind: 'ok' })
  })
  it('500 is unreachable with the status text', async () => {
    expect(await loadOverview(respond(500, 'boom'))).toMatchObject({ kind: 'unreachable', detail: 'API antwortet mit 500' })
  })
  it('a 200 without status or incidents is unreachable', async () => {
    expect((await loadOverview(respond(200, { foo: 1 }))).kind).toBe('unreachable')
  })
  it('non-JSON is unreachable', async () => {
    expect((await loadOverview(respond(200, '<html>'))).kind).toBe('unreachable')
  })
  it('network error is unreachable', async () => {
    expect(await loadOverview(fail)).toMatchObject({ kind: 'unreachable', detail: 'Backend nicht erreichbar' })
  })
})

describe('duration', () => {
  it('reads seconds, minutes, hours and days', () => {
    expect(duration(45)).toBe('45 s')
    expect(duration(121)).toBe('2 min')
    expect(duration(3600)).toBe('1 h 0 min')
    expect(duration(90_000)).toBe('1 T 1 h')
  })
})

describe('verdictText', () => {
  it('names the problem count, the uncertain count, or the calm state', () => {
    expect(verdictText('PROBLEM', overviewReport.counts)).toBe('Braucht dich: 1')
    expect(verdictText('UNCERTAIN', { problem: 0, uncertain: 2, ok: 8, stopped: 0, paused: 0 })).toBe('Unklar: 2')
    expect(verdictText('CALM', calmReport.counts)).toBe('Nichts gestört')
  })
})

describe('overviewCounts', () => {
  it('maps the report counts and never claims a problem in a calm state', () => {
    expect(overviewCounts(overviewReport.counts)).toEqual([['Gestört', 1], ['Nicht erreichbar', 0], ['Läuft', 11]])
    expect(overviewCounts(calmReport.counts)).toEqual([['Gestört', 0], ['Nicht erreichbar', 0], ['Läuft', 12]])
  })
  it('feeds a calm readout that shows no problem', () => {
    const html = renderToStaticMarkup(createElement(Readout, { counts: overviewCounts(calmReport.counts), checkedAt: calmReport.checkedAt }))
    expect(html).toContain('Gestört')
    expect(html).toContain('12')
  })
})

describe('OverviewView', () => {
  it('raises a problem incident with its label, detail, duration and link to Dienste', () => {
    const html = renderToStaticMarkup(createElement(OverviewView, { load: { kind: 'ok', report: overviewReport }, onShowDienste: () => {} }))
    expect(html).toContain('Braucht dich: 1')
    expect(html).toContain('api.example.org')
    expect(html).toContain('Ausgefallen')
    expect(html).toContain('seit 3 min')
    expect(html).toContain('HTTP 503')
    expect(html).toContain('Zum Dienst')
  })
  it('stays quiet but still names the seven quiet days', () => {
    const html = renderToStaticMarkup(createElement(OverviewView, { load: { kind: 'ok', report: calmReport }, onShowDienste: () => {} }))
    expect(html).toContain('Nichts gestört')
    expect(html).toContain('Keine Unterbrechungen in den letzten sieben Tagen')
    expect(html).not.toContain('Braucht dich')
  })
  it('shows the recent outages of the last seven days', () => {
    const html = renderToStaticMarkup(createElement(OverviewView, { load: { kind: 'ok', report: overviewReport }, onShowDienste: () => {} }))
    expect(html).toContain('Letzte sieben Tage')
    expect(html).toContain('shop.example.org')
    expect(html).toContain('2 min')
  })
  it('shows an unreachable source without relying on colour', () => {
    const source: OverviewSource = { name: 'containers', state: 'UNREACHABLE', label: 'Nicht erreichbar', lastSeenAt: null }
    const report: OverviewReport = { ...overviewReport, sources: [source] }
    const html = renderToStaticMarkup(createElement(OverviewView, { load: { kind: 'ok', report }, onShowDienste: () => {} }))
    expect(html).toContain('Container')
    expect(html).toContain('Nicht erreichbar')
  })
  it('a failed API is an alert, not an all-fine state', () => {
    const html = renderToStaticMarkup(createElement(OverviewView, { load: { kind: 'unreachable', detail: 'refused', checkedAt: '2026-11-28T07:12:04Z' }, onShowDienste: () => {} }))
    expect(html).toContain('role="alert"')
    expect(html).not.toContain('Nichts gestört')
  })
})
