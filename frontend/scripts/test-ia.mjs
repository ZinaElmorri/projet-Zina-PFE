// Vérification de bout en bout de la partie IA : node scripts/test-ia.mjs
import { chromium } from 'playwright'

const FRONT = 'http://localhost:5173'
const API = 'http://localhost:8080/api'
const MAILHOG = 'http://localhost:8025'
const resultats = []
const ok = (nom, cond, detail = '') => { resultats.push(`${cond ? 'OK ' : 'KO '} ${nom}${detail ? ' - ' + detail : ''}`); if (!cond) process.exitCode = 1 }
const pause = (ms) => new Promise((r) => setTimeout(r, ms))

const post = (url, body, token) => fetch(url, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
  body: body === undefined ? undefined : JSON.stringify(body),
})
const get = (url, token) => fetch(url, { headers: { Authorization: `Bearer ${token}` } })

async function session(username) {
  await fetch(`${MAILHOG}/api/v1/messages`, { method: 'DELETE' })
  const defi = await post(`${API}/auth/login`, { username, password: `${username}123` }).then((r) => r.json())
  const mails = await fetch(`${MAILHOG}/api/v2/messages`).then((r) => r.json())
  const code = mails.items[0].Content.Body.match(/\b\d{6}\b/)[0]
  return post(`${API}/auth/verify-otp`, { challengeId: defi.challengeId, code }).then((r) => r.json())
}

async function attendreMetriques(token) {
  for (let i = 0; i < 40; i++) {
    const m = await get(`${API}/admin/ai/metrics`, token).then((r) => r.json())
    if (m.etat !== 'EN_COURS') return m
    await pause(1500)
  }
  return null
}

const admin = await session('admin')
const rh = await session('rh')
const auditeur = await session('auditeur')

// --- API : droits et contenu ---
const etat = await get(`${API}/admin/ai/health`, admin.token).then((r) => r.json())
ok('admin : module IA disponible', etat.disponible === true, etat.modelVersion)
const m = await attendreMetriques(admin.token)
ok('métriques disponibles (5 modèles)', m?.etat === 'DISPONIBLE' && m.evaluation.modeles.length === 5)
const service = m?.evaluation.modeles.find((x) => x.enService)
ok('modèle en service : rappel >= 0,9', service?.metriques.rappel.moyenne >= 0.9,
  `F1=${service?.metriques.f1.moyenne} PR-AUC=${service?.metriques.prAuc.moyenne}`)
ok('retours utilisateurs présents', m && 'precisionObservee' in m.retours)
ok('RH : métriques consultables', (await get(`${API}/admin/ai/metrics`, rh.token)).status === 200)
ok('RH : réentraînement refusé (403)', (await post(`${API}/admin/ai/train?source=synthetique`, undefined, rh.token)).status === 403)
ok('Auditeur : supervision IA refusée (403)', (await get(`${API}/admin/ai/metrics`, auditeur.token)).status === 403)
ok('Auditeur : relancer la détection refusé (403)', (await post(`${API}/anomalies/detecter`, undefined, auditeur.token)).status === 403)
ok('Ancien endpoint /transactions/analyser supprimé', [404, 405, 403].includes((await post(`${API}/transactions/analyser`, undefined, admin.token)).status))

// --- Réentraînement réel puis synthétique ---
const reel = await post(`${API}/admin/ai/train?source=reel`, undefined, admin.token)
const reelBody = await reel.json()
ok('admin : réentraînement sur données réelles', reel.status === 200 && reelBody.modelVersion !== etat.modelVersion, reelBody.message || reelBody.erreur)
const apresReel = await get(`${API}/admin/ai/health`, admin.token).then((r) => r.json())
ok('source du modèle = reel', apresReel.source === 'reel', `${apresReel.nbEntrainement} paiements`)
const mReel = await attendreMetriques(admin.token)
ok('métriques recalculées pour la nouvelle version', mReel?.evaluation?.modelVersion === apresReel.modelVersion)
const synth = await post(`${API}/admin/ai/train?source=synthetique`, undefined, admin.token)
ok('admin : retour au modèle synthétique', synth.status === 200)
await attendreMetriques(admin.token)

// --- Interface ---
const browser = await chromium.launch()
async function page(s) {
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  await ctx.addInitScript((d) => { localStorage.setItem('token', d.token); localStorage.setItem('username', d.username); localStorage.setItem('role', d.role) }, s)
  return { ctx, p: await ctx.newPage() }
}

const a = await page(admin)
await a.p.goto(`${FRONT}/`, { waitUntil: 'networkidle' })
ok('admin : plus d’onglet Intelligence artificielle', await a.p.locator('.app-sider').getByText('Intelligence artificielle').count() === 0)

await a.p.goto(`${FRONT}/transactions`, { waitUntil: 'networkidle' })
ok('Transactions : un seul bouton IA', await a.p.getByRole('button', { name: 'Analyser (IA)' }).count() === 1
  && await a.p.getByRole('button', { name: /Analyser montants/ }).count() === 0)
await a.p.getByRole('button', { name: 'Analyser (IA)' }).click()
await a.p.locator('.ia-alert').first().waitFor({ timeout: 30000 })
ok('Transactions : résultat de l’analyse IA affiché', (await a.p.locator('.ia-alert').first().innerText()).includes('modèle'))

await a.p.goto(`${FRONT}/virements-masse`, { waitUntil: 'networkidle' })
const periode = a.p.getByRole('combobox', { name: /Période de paie/ })
const options = await periode.locator('option').allInnerTexts()
if (options.length > 1) {
  await periode.selectOption({ index: 1 })
  await a.p.getByRole('button', { name: 'Rechercher' }).click()
  await a.p.getByRole('button', { name: 'Contrôle IA des montants' }).waitFor({ timeout: 30000 })
  await a.p.getByRole('button', { name: 'Contrôle IA des montants' }).click()
  await a.p.locator('.ia-alert').filter({ hasText: 'Contrôle IA' }).waitFor({ timeout: 60000 })
  ok('Virements de masse : contrôle IA', true, (await a.p.locator('.ia-alert').filter({ hasText: 'Contrôle IA' }).innerText()).split('\n')[0])
} else {
  ok('Virements de masse : aucune période disponible pour tester', false)
}
await a.ctx.close()

const r = await page(rh)
await r.p.goto(`${FRONT}/ia`, { waitUntil: 'networkidle' })
ok('route /ia supprimée (retour au tableau de bord)', new URL(r.p.url()).pathname === '/')
await r.ctx.close()

const au = await page(auditeur)
await au.p.goto(`${FRONT}/anomalies`, { waitUntil: 'networkidle' })
await au.p.locator('.content-panel tbody tr').first().waitFor()
ok('Auditeur : pas de menu IA', await au.p.locator('.app-sider').getByText('Intelligence artificielle').count() === 0)
ok('Auditeur : anomalies en lecture seule', await au.p.getByRole('button', { name: 'Relancer la détection' }).count() === 0
  && await au.p.getByRole('button', { name: 'Ignorer' }).count() === 0)
await au.ctx.close()

await browser.close()
console.log(resultats.join('\n'))
