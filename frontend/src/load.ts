import type { CheckHistory, ChecksReport, ContainerReport, HistoryBucket, Load } from './types'

/** Any answer that is not the expected report counts as unreachable — never a silent empty list. */
async function loadReport<T>(
  path: string,
  valid: (body: Record<string, unknown>) => boolean,
  fetchFn: typeof fetch,
): Promise<Load<T>> {
  const now = new Date().toISOString()
  try {
    const res = await fetchFn(path)
    const body = (await res.json().catch(() => null)) as Record<string, unknown> | null
    if (res.ok && body && valid(body)) return { kind: 'ok', report: body as unknown as T }
    return {
      kind: 'unreachable',
      detail: typeof body?.detail === 'string' ? body.detail : `API antwortet mit ${res.status}`,
      checkedAt: typeof body?.checkedAt === 'string' ? body.checkedAt : now,
    }
  } catch {
    return { kind: 'unreachable', detail: 'Backend nicht erreichbar', checkedAt: now }
  }
}

export const loadContainers = (fetchFn: typeof fetch = fetch): Promise<Load<ContainerReport>> =>
  loadReport('/api/containers', (b) => Array.isArray(b.containers), fetchFn)

export const loadChecks = (fetchFn: typeof fetch = fetch): Promise<Load<ChecksReport>> =>
  loadReport('/api/checks', (b) => Array.isArray(b.checks), fetchFn)

/** 24 h history buckets for one check; null when the strip cannot be shown (kept out of the page state). */
export async function loadHistory(id: number, fetchFn: typeof fetch = fetch): Promise<HistoryBucket[] | null> {
  try {
    const res = await fetchFn(`/api/checks/${id}/history?hours=24`)
    const body = (await res.json().catch(() => null)) as CheckHistory | null
    if (res.ok && body && Array.isArray(body.buckets)) return body.buckets
  } catch {
    // no strip is worse than a stub strip: keep the page state untouched
  }
  return null
}

/** Bar colour for one bucket: red when checks failed, empty when nothing ran, ok otherwise. */
export function bucketClass(b: HistoryBucket): string {
  if (b.total === 0) return 'strip-empty'
  return b.failed > 0 ? 'strip-bad' : 'strip-ok'
}

/** Counts per label, in the given urgency order; labels not listed come last. */
export function countBy(items: { label: string }[], order: string[]): [string, number][] {
  const counts = new Map<string, number>()
  for (const item of items) counts.set(item.label, (counts.get(item.label) ?? 0) + 1)
  const rank = (label: string) => {
    const i = order.indexOf(label)
    return i < 0 ? order.length : i
  }
  return [...counts].sort((a, b) => rank(a[0]) - rank(b[0]))
}

export const clock = (iso: string) => new Date(iso).toLocaleTimeString('de-CH')

/** Urgency order for the header readout. */
export const CHECK_ORDER = ['Ausgefallen', 'Wird geprüft', 'Erreichbar', 'Pausiert']
export const CONTAINER_ORDER = ['Gestört', 'Nicht erreichbar', 'Unbekannt', 'Gestoppt', 'Läuft']

/** "Seit" reads as a time today, as date + time on older states. */
export function sinceText(iso: string, now = new Date()): string {
  const d = new Date(iso)
  return d.toDateString() === now.toDateString()
    ? d.toLocaleTimeString('de-CH')
    : d.toLocaleString('de-CH', { day: '2-digit', month: '2-digit', hour: '2-digit', minute: '2-digit' })
}

/** Host shown under a check name; the whole URL stays in the title. */
export function hostOf(url: string): string {
  try {
    return new URL(url).host
  } catch {
    return url
  }
}

export const pct = (v: number | null) => (v == null ? '—' : `${v.toFixed(1).replace('.', ',')} %`)
