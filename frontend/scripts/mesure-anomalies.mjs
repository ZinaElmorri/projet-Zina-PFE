// Mesure : temps API /anomalies et temps d'affichage de la page : node scripts/mesure-anomalies.mjs
import { chromium } from 'playwright'

const FRONT = 'http://localhost:5173'
const API = 'http://localhost:8080/api'
const MAILHOG = 'http://localhost:8025'
const post = (u, b) => fetch(u, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(b) }).then((r) => r.json())

await fetch(`${MAILHOG}/api/v1/messages`, { method: 'DELETE' })
const defi = await post(`${API}/auth/login`, { username: 'admin', password: 'admin123' })
const mails = await fetch(`${MAILHOG}/api/v2/messages`).then((r) => r.json())
const s = await post(`${API}/auth/verify-otp`, { challengeId: defi.challengeId, code: mails.items[0].Content.Body.match(/\b\d{6}\b/)[0] })

let t = Date.now()
const res = await fetch(`${API}/anomalies`, { headers: { Authorization: `Bearer ${s.token}` } })
const txt = await res.text()
console.log(`API /anomalies : ${Date.now() - t} ms, ${JSON.parse(txt).length} lignes, ${(txt.length / 1024).toFixed(0)} Ko`)

const browser = await chromium.launch()
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
await ctx.addInitScript((d) => { localStorage.setItem('token', d.token); localStorage.setItem('username', d.username); localStorage.setItem('role', d.role) }, s)
const page = await ctx.newPage()
await page.goto(`${FRONT}/`, { waitUntil: 'networkidle' })
t = Date.now()
await page.locator('.app-sider').getByText('Anomalies', { exact: true }).click()
await page.waitForFunction(() => document.querySelectorAll('.content-panel tbody tr').length > 1, null, { timeout: 60000 })
console.log(`Page Anomalies : premières lignes affichées après ${Date.now() - t} ms (${await page.locator('.content-panel tbody tr').count()} lignes dans le DOM)`)
console.log(`Compteurs : ${await page.locator('.content-panel .info').first().innerText()}`)
const pagination = page.locator('.ant-pagination')
if (await pagination.count()) {
  console.log(`Pagination : ${await page.locator('.ant-pagination-total-text').innerText()}`)
  await pagination.getByTitle('2', { exact: true }).click()
  console.log(`Page 2 : ${await page.locator('.ant-pagination-total-text').innerText()}, ${await page.locator('.content-panel tbody tr').count()} lignes`)
}
t = Date.now()
await page.getByRole('button', { name: 'Corrigées' }).click()
await page.waitForFunction(() => !document.querySelector('.content-panel .info .ant-spin'), null, { timeout: 30000 })
console.log(`Filtre Corrigées : ${Date.now() - t} ms, ${await page.locator('.content-panel tbody tr').count()} lignes, statuts = ${[...new Set(await page.locator('.content-panel .badge-statut').allInnerTexts())].join(',')}`)
await browser.close()
