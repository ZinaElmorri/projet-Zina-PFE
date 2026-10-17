// Vérifie qu'aucune page ne déborde horizontalement : node scripts/test-debordement.mjs
import { chromium } from 'playwright'

const FRONT = 'http://localhost:5173'
const API = 'http://localhost:8080/api'
const MAILHOG = 'http://localhost:8025'
const pages = ['/', '/kpi', '/transactions', '/anomalies', '/virements-masse', '/xsd', '/audit', '/users', '/profil']

const post = (u, b) => fetch(u, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(b) }).then((r) => r.json())
await fetch(`${MAILHOG}/api/v1/messages`, { method: 'DELETE' })
const defi = await post(`${API}/auth/login`, { username: 'admin', password: 'admin123' })
const mails = await fetch(`${MAILHOG}/api/v2/messages`).then((r) => r.json())
const s = await post(`${API}/auth/verify-otp`, { challengeId: defi.challengeId, code: mails.items[0].Content.Body.match(/\b\d{6}\b/)[0] })

const browser = await chromium.launch()
for (const largeur of [1280, 1440]) {
  const ctx = await browser.newContext({ viewport: { width: largeur, height: 900 } })
  await ctx.addInitScript((d) => { localStorage.setItem('token', d.token); localStorage.setItem('username', d.username); localStorage.setItem('role', d.role) }, s)
  const page = await ctx.newPage()
  for (const p of pages) {
    await page.goto(FRONT + p, { waitUntil: 'networkidle' })
    await page.waitForTimeout(500)
    const r = await page.evaluate(() => {
      const panneau = document.querySelector('.content-panel')?.getBoundingClientRect()
      const debordants = [...document.querySelectorAll('.content-panel table')]
        .filter((t) => panneau && t.getBoundingClientRect().right > panneau.right + 1 && !t.closest('[style*="overflow"], .masse-bloc, .content-panel'))
      return { page: document.documentElement.scrollWidth - document.documentElement.clientWidth, debordants: debordants.length }
    })
    const ok = r.page <= 0
    if (!ok) process.exitCode = 1
    console.log(`${ok ? 'OK ' : 'KO '} ${largeur}px ${p} (débordement page : ${Math.max(r.page, 0)} px)`)
  }
  if (largeur === 1440) {
    await page.goto(`${FRONT}/virements-masse`, { waitUntil: 'networkidle' })
    await page.getByText('Historique des fichiers générés').scrollIntoViewIfNeeded()
    await page.screenshot({ path: 'test-debordement.png' })
  }
  await ctx.close()
}
await browser.close()
