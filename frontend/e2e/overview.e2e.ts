import { expect, test, type Page } from '@playwright/test'

// Synthetic data only: this repository is public.
const checkedAt = '2026-11-28T07:12:04Z'
const sources = [
  { name: 'checks', state: 'OK', label: 'Aktuell', lastSeenAt: checkedAt },
  { name: 'containers', state: 'OK', label: 'Aktuell', lastSeenAt: checkedAt },
  { name: 'heartbeat', state: 'OK', label: 'Aktuell', lastSeenAt: checkedAt },
]

const reports = {
  problem: {
    checkedAt, sources, status: 'PROBLEM', counts: { problem: 1, uncertain: 0, ok: 11, stopped: 2, paused: 0 },
    incidents: [{ kind: 'CHECK', severity: 'PROBLEM', name: 'api.example.org', state: 'DOWN', label: 'Ausgefallen',
      since: '2026-11-28T07:08:31Z', detail: 'HTTP 503 (erwartet 200-399)', checkId: 7 }],
    recentOutages: [{ checkId: 7, name: 'api.example.org', start: '2026-11-27T04:33:54Z', end: '2026-11-27T04:35:55Z',
      durationSeconds: 121, failures: 2 }],
  },
  calm: {
    checkedAt, sources, status: 'CALM', counts: { problem: 0, uncertain: 0, ok: 12, stopped: 2, paused: 0 },
    incidents: [], recentOutages: [],
  },
  dockerGone: {
    checkedAt, status: 'UNCERTAIN', counts: { problem: 0, uncertain: 1, ok: 9, stopped: 0, paused: 0 },
    sources: [sources[0], { name: 'containers', state: 'UNREACHABLE', label: 'Nicht erreichbar', lastSeenAt: null }, sources[2]],
    incidents: [{ kind: 'SOURCE', severity: 'UNCERTAIN', name: 'Container', state: 'UNREACHABLE', label: 'Nicht erreichbar',
      since: null, detail: null, checkId: null }],
    recentOutages: [],
  },
}

async function open(page: Page, report: object) {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(String(e)))
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text()) })
  await page.route('**/api/overview', (r) => r.fulfill({ json: report }))
  await page.route('**/api/checks', (r) => r.fulfill({ json: { checkedAt, checks: [] } }))
  await page.route('**/api/containers', (r) => r.fulfill({ json: { checkedAt, containers: [] } }))
  await page.goto('/')
  return errors
}

const noHorizontalOverflow = (page: Page) =>
  page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)

test('Problem: Urteil, Vorfall mit Ursache, Sprung zum Dienst', async ({ page }) => {
  const errors = await open(page, reports.problem)
  await expect(page.getByRole('heading', { name: 'Braucht dich: 1' })).toBeVisible()
  await expect(page.getByText('HTTP 503 (erwartet 200-399)')).toBeVisible()
  expect(await noHorizontalOverflow(page)).toBe(true)
  await page.getByRole('button', { name: /Zum Dienst/ }).click()
  await expect(page.getByRole('button', { name: 'Dienste' })).toHaveAttribute('aria-pressed', 'true')
  expect(errors).toEqual([])
})

test('Ruhig: kein Fehlerzähler im Kopf, leere Ausfallliste', async ({ page }) => {
  const errors = await open(page, reports.calm)
  await expect(page.getByRole('heading', { name: 'Nichts gestört' })).toBeVisible()
  await expect(page.getByText('Keine Unterbrechungen in den letzten sieben Tagen')).toBeVisible()
  await expect(page.locator('header')).not.toContainText(/[1-9]\d* Gestört/)
  expect(await noHorizontalOverflow(page)).toBe(true)
  expect(errors).toEqual([])
})

test('Docker weg: unklar statt ruhig', async ({ page }) => {
  const errors = await open(page, reports.dockerGone)
  await expect(page.getByRole('heading', { name: 'Unklar: 1' })).toBeVisible()
  await expect(page.getByRole('heading', { name: 'Nichts gestört' })).toHaveCount(0)
  await expect(page.getByText('Nicht erreichbar').first()).toBeVisible()
  expect(errors).toEqual([])
})

test('Tabs lassen sich per Tastatur auslösen', async ({ page }) => {
  await open(page, reports.problem)
  // shortcut: focus set directly, because WebKit skips buttons on Tab unless the OS keyboard-navigation setting is on
  await page.getByRole('button', { name: 'Dienste' }).focus()
  await page.keyboard.press('Enter')
  await expect(page.getByRole('button', { name: 'Dienste' })).toHaveAttribute('aria-pressed', 'true')
})
