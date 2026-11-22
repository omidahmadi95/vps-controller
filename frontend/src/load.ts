import type { ContainerReport, Load } from './types'

/** Maps the API answer to a page state. Anything that is not a valid report counts as unreachable. */
export async function loadContainers(fetchFn: typeof fetch = fetch): Promise<Load> {
  const now = new Date().toISOString()
  try {
    const res = await fetchFn('/api/containers')
    const body = await res.json().catch(() => null)
    if (res.ok && body && Array.isArray(body.containers)) return { kind: 'ok', report: body as ContainerReport }
    return { kind: 'unreachable', detail: body?.detail ?? `API antwortet mit ${res.status}`, checkedAt: body?.checkedAt ?? now }
  } catch {
    return { kind: 'unreachable', detail: 'Backend nicht erreichbar', checkedAt: now }
  }
}
