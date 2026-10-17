// Vérification fonctionnelle de l'étape 1 (layout) : node scripts/test-layout.mjs
import { chromium } from 'playwright'

const FRONT = 'http://localhost:5173'
const API = 'http://localhost:8080/api'
const MAILHOG = 'http://localhost:8025'
const resultats = []
const ok = (nom, cond) => { resultats.push(`${cond ? 'OK ' : 'KO '} ${nom}`); if (!cond) process.exitCode = 1 }

const dernierCode = async () => {
  const mails = await fetch(`${MAILHOG}/api/v2/messages`).then((r) => r.json())
  return mails.items[0].Content.Body.match(/\b\d{6}\b/)[0]
}

const browser = await chromium.launch()

// 1. Connexion complète par l'interface (mot de passe + OTP) en admin
await fetch(`${MAILHOG}/api/v1/messages`, { method: 'DELETE' })
const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 } })
const page = await ctx.newPage()
await page.goto(`${FRONT}/login`)
await page.getByLabel('Utilisateur').fill('admin')
await page.getByLabel('Mot de passe').fill('admin123')
await page.getByRole('button', { name: 'Se connecter' }).click()
await page.getByLabel('Code de vérification').waitFor()
await page.getByLabel('Code de vérification').fill(await dernierCode())
await page.getByRole('button', { name: 'Vérifier' }).click()
await page.waitForURL(`${FRONT}/`)
ok('connexion admin mot de passe + OTP', page.url() === `${FRONT}/`)

// 2. Menu : chaque entrée navigue et met à jour le fil d'Ariane
const entrees = [
  ['Indicateurs KPI', '/kpi', 'Indicateurs KPI'],
  ['Transactions', '/transactions', 'Transactions'],
  ['Virements de masse', '/virements-masse', 'Virements de masse'],
  ['Historique SEPA', '/virements-masse?onglet=historique', 'Historique SEPA'],
  ['Anomalies', '/anomalies', 'Anomalies'],
  ['Schémas XSD', '/xsd', 'Schémas XSD'],
  ['Utilisateurs', '/users', 'Utilisateurs'],
  ["Journal d'audit", '/audit', "Journal d'audit"],
  ['Tableau de bord', '/', 'Tableau de bord'],
]
for (const [label, chemin, ariane] of entrees) {
  await page.locator('.app-sider').getByText(label, { exact: true }).click()
  await page.waitForURL(FRONT + chemin)
  await page.waitForFunction(() => {
    const p = document.querySelector('.content-panel')
    return p && !p.querySelector('.ant-skeleton') && p.innerText.trim().length > 0
  }, null, { timeout: 15000 }).catch(() => {})
  const fil = await page.locator('.ant-breadcrumb').innerText()
  const contenu = await page.locator('.content-panel').innerText()
  ok(`menu « ${label} » -> ${chemin}, fil d'Ariane, contenu affiché`, fil.includes(ariane) && contenu.trim().length > 0)
}
await page.screenshot({ path: 'test-layout-admin.png' })

// 3. Repli / dépli de la barre latérale
await page.locator('.ant-layout-sider-trigger').click()
ok('barre latérale repliée', await page.locator('.ant-layout-sider-collapsed').count() === 1)
await page.locator('.ant-layout-sider-trigger').click()
ok('barre latérale dépliée', await page.locator('.ant-layout-sider-collapsed').count() === 0)

// 4. Menu utilisateur : nom, rôle, Profil, Déconnexion
const entete = await page.locator('.app-user').innerText()
ok('en-tête : nom + rôle', entete.includes('admin') && entete.includes('Administrateur'))
await page.locator('.app-user').click()
await page.getByRole('menuitem', { name: 'Profil' }).click()
await page.waitForURL(`${FRONT}/profil`)
ok('menu utilisateur -> Profil', page.url().endsWith('/profil'))
await page.locator('.app-user').click()
await page.getByRole('menuitem', { name: 'Déconnexion' }).click()
await page.waitForURL(`${FRONT}/login`)
ok('menu utilisateur -> Déconnexion', page.url().endsWith('/login') && await page.evaluate(() => !localStorage.getItem('token')))
await ctx.close()

// 5. Rôle non administrateur : « Utilisateurs » absent
await fetch(`${MAILHOG}/api/v1/messages`, { method: 'DELETE' })
const post = (u, b) => fetch(u, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(b) }).then((r) => r.json())
const defi = await post(`${API}/auth/login`, { username: 'rh', password: 'rh123' })
const rh = await post(`${API}/auth/verify-otp`, { challengeId: defi.challengeId, code: await dernierCode() })
const ctxRh = await browser.newContext({ viewport: { width: 1440, height: 900 } })
await ctxRh.addInitScript((s) => { localStorage.setItem('token', s.token); localStorage.setItem('username', s.username); localStorage.setItem('role', s.role) }, rh)
const pageRh = await ctxRh.newPage()
await pageRh.goto(FRONT)
await pageRh.locator('.app-sider').waitFor()
ok('rôle RH : entrée Utilisateurs masquée', await pageRh.locator('.app-sider').getByText('Utilisateurs', { exact: true }).count() === 0)
ok('rôle RH : tag Gestionnaire RH', (await pageRh.locator('.app-user').innerText()).includes('Gestionnaire RH'))
await ctxRh.close()

// 6. Sans token : la route protégée redirige vers /login (ProtectedRoute inchangé)
const ctxAnonyme = await browser.newContext()
const pageAnonyme = await ctxAnonyme.newPage()
await pageAnonyme.goto(`${FRONT}/transactions`)
await pageAnonyme.waitForURL(`${FRONT}/login`, { timeout: 15000 }).catch(() => {})
ok('sans session -> redirection /login', pageAnonyme.url().endsWith('/login'))
await ctxAnonyme.close()

await browser.close()
console.log(resultats.join('\n'))
