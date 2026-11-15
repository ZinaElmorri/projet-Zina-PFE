// Captures d'écran de l'intégration de l'IA (étape C) pour le rapport.
//
// Prérequis : application lancée (Docker), scénario importé (python -m benchmark.importer_scenario),
//   npm i puppeteer-core (dans un dossier temporaire) et Microsoft Edge installé.
// Usage    : node capturer_ecrans.mjs [dossier_de_sortie]
//
// Le script se connecte avec les comptes de démonstration (mot de passe : <nom>123 ; OTP lu dans MailHog),
// lance le contrôle du fichier du scénario en tant qu'expert1, puis photographie les écrans.
import puppeteer from 'puppeteer-core'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const API = 'http://localhost:8080/api'
const WEB = 'http://localhost:5173'
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const SORTIE = process.argv[2] || 'captures'
mkdirSync(SORTIE, { recursive: true })

async function connexion(username, password) {
  let r = await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) })
  let d = await r.json()
  if (!d.otpRequis) return d
  await new Promise((s) => setTimeout(s, 1500))
  const m = await (await fetch('http://localhost:8025/api/v2/messages?limit=1')).json()
  const code = m.items[0].Content.Body.replace(/=\r?\n/g, '').match(/\b(\d{6})\b/)[1]
  r = await fetch(`${API}/auth/verify-otp`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ challengeId: d.challengeId, code }) })
  return await r.json()
}
const appel = (tok, methode, chemin, corps) => fetch(`${API}${chemin}`, {
  method: methode, headers: { Authorization: `Bearer ${tok}`, 'Content-Type': 'application/json' }, body: corps ? JSON.stringify(corps) : undefined,
}).then((r) => r.json())

const expert = await connexion('expert1', 'expert1123')
const periode = new Date().toISOString().slice(0, 7)
const fichier = await appel(expert.token, 'POST', '/masse/controle', {
  xsdSchemaId: 3, clientId: 'SOC-DEMO', periode, matricules: [], transactionIds: [],
  nomDebiteur: 'Société de démonstration SEPAPaie', ibanDebiteur: 'FR7630004000031234567890143', bicDebiteur: 'BNPAFRPP',
  dateExecution: new Date(Date.now() + 86400000).toISOString().slice(0, 10), statuts: ['APPROUVEE'], exclureErreurs: false,
})
console.log('Fichier de contrôle créé :', fichier.id, `(${fichier.nbSuspectsIa} fiches suspectes)`)

const navigateur = await puppeteer.launch({ executablePath: EDGE, headless: 'new', args: ['--no-sandbox'] })
async function ecran(session, chemin, nom, { attente = 1200, complet = false, hauteur = 900, recadrage } = {}) {
  const page = await navigateur.newPage()
  await page.setViewport({ width: 1440, height: hauteur, deviceScaleFactor: 1.5 })
  await page.evaluateOnNewDocument((s) => {
    localStorage.setItem('token', s.token); localStorage.setItem('username', s.username); localStorage.setItem('role', s.role)
  }, session)
  await page.goto(WEB + chemin, { waitUntil: 'networkidle0' })
  await new Promise((r) => setTimeout(r, attente))
  await page.screenshot({ path: join(SORTIE, nom), fullPage: complet, ...(recadrage ? { clip: recadrage } : {}) })
  await page.close()
  console.log('capture', nom)
}

const admin = await connexion('admin', 'admin123')
const manager = await connexion('manager1', 'manager1123')
await ecran(admin, '/qualite-ia', 'capture_qualite_modele_ia.png', { complet: true })
await ecran(expert, `/virements-masse/resultat/${fichier.id}/synthese`, 'capture_synthese_controle_ia.png', { recadrage: { x: 240, y: 56, width: 1200, height: 430 } })
await ecran(expert, `/virements-masse/resultat/${fichier.id}/controle-ia`, 'capture_sous_page_controle_ia.png', { complet: true })
await ecran(manager, '/kpi', 'capture_kpi_ia.png', { complet: true })
await navigateur.close()
console.log('Fichier de contrôle utilisé :', fichier.id)
