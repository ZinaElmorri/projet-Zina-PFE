// Captures d'écran de toutes les pages : node scripts/captures.mjs avant|apres
// Prérequis : application lancée (docker compose up -d), compte admin, MailHog pour l'OTP.
import { chromium } from 'playwright'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'
import { fileURLToPath } from 'node:url'

const dossier = process.argv[2] || 'avant'
const out = fileURLToPath(new URL(`../../docs/ui/${dossier}/`, import.meta.url))
const FRONT = 'http://localhost:5173'
const API = 'http://localhost:8080/api'
const MAILHOG = 'http://localhost:8025'

const pagesPubliques = { login: '/login', register: '/register' }
const pagesPrivees = {
  dashboard: '/', kpi: '/kpi', transactions: '/transactions', anomalies: '/anomalies',
  'virements-masse': '/virements-masse', xsd: '/xsd', audit: '/audit', users: '/users', profil: '/profil',
}

const post = (url, body) => fetch(url, {
  method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body),
}).then((r) => r.json())

async function session() {
  await fetch(`${MAILHOG}/api/v1/messages`, { method: 'DELETE' })
  const defi = await post(`${API}/auth/login`, { username: 'admin', password: 'admin123' })
  if (!defi.otpRequis) return defi
  const mails = await fetch(`${MAILHOG}/api/v2/messages`).then((r) => r.json())
  const code = mails.items[0].Content.Body.match(/\b\d{6}\b/)[0]
  return post(`${API}/auth/verify-otp`, { challengeId: defi.challengeId, code })
}

mkdirSync(out, { recursive: true })
const s = await session()
const browser = await chromium.launch()

async function capturer(pages, avecSession) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  if (avecSession) {
    await ctx.addInitScript((d) => {
      localStorage.setItem('token', d.token)
      localStorage.setItem('username', d.username)
      localStorage.setItem('role', d.role)
    }, s)
  }
  const page = await ctx.newPage()
  for (const [nom, chemin] of Object.entries(pages)) {
    await page.goto(FRONT + chemin, { waitUntil: 'networkidle' })
    await page.waitForTimeout(800)
    // Pages très longues (milliers de lignes) : capture limitée aux 4000 premiers pixels.
    const hauteur = Math.min(await page.evaluate(() => document.documentElement.scrollHeight), 4000)
    await page.screenshot({
      path: join(out, `${nom}.png`),
      fullPage: true,
      clip: { x: 0, y: 0, width: 1440, height: hauteur },
      timeout: 90000,
    })
    console.log(`capture ${dossier}/${nom}.png`)
  }
  await ctx.close()
}

await capturer(pagesPubliques, false)
await capturer(pagesPrivees, true)
await browser.close()
