// Démo automatisée SEPAPaie (≈ 4 min 40 s) : pilote l'application déployée dans Edge (fenêtre visible)
// pour qu'elle soit enregistrée (Win+G, OBS…). Un bandeau en bas d'écran affiche la narration de chaque étape.
// Prérequis : conteneurs Docker démarrés, ai-module\.venv présent, puppeteer-core installé (captures_rapport).
import { createRequire } from 'node:module'
import { execSync } from 'node:child_process'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const require = createRequire(process.env.DEMO_PUPPETEER_DIR || 'C:\\Users\\zelmorri\\AppData\\Local\\Temp\\captures_rapport\\package.json')
const puppeteer = require('puppeteer-core')

const API = 'http://localhost:8080/api'
const WEB = 'http://localhost:5173'
const MAILHOG = 'http://localhost:8025'
const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const RACINE = 'C:\\Users\\zelmorri\\Documents\\PFE_Finale\\projet-Zina-PFE'
const AI = RACINE + '\\ai-module'
const PY = AI + '\\.venv\\Scripts\\python.exe'
const ENV = { ...process.env, PATH: process.env.PATH + ';C:\\Program Files\\Docker\\Docker\\resources\\bin' }
const SORTIE = join(RACINE, 'demo_captures')
mkdirSync(SORTIE, { recursive: true })

const pause = (ms) => new Promise((r) => setTimeout(r, ms >= 2000 ? ms * 1.5 : ms))
const remonter = () => page.evaluate(() => { window.scrollTo(0, 0); document.querySelector('.app-content')?.scrollTo(0, 0) })
let debut = Date.now()
const horloge = () => { const s = Math.round((Date.now() - debut) / 1000); return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}` }

async function codeOtp() {
  await pause(1500)
  const m = await (await fetch(`${MAILHOG}/api/v2/messages?limit=1`)).json()
  return m.items[0].Content.Body.replace(/=\r?\n/g, '').match(/\b(\d{6})\b/)[1]
}
async function jeton(username, password) {
  const d = await (await fetch(`${API}/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ username, password }) })).json()
  if (!d.otpRequis) return d
  const code = await codeOtp()
  return (await (await fetch(`${API}/auth/verify-otp`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ challengeId: d.challengeId, code }) })).json())
}
const appel = async (t, m, p, corps) => (await fetch(`${API}${p}`, { method: m, headers: { Authorization: `Bearer ${t}`, 'Content-Type': 'application/json' }, body: corps ? JSON.stringify(corps) : undefined })).json()

// --- Préparation (hors enregistrement) : données de démonstration et fichier propre déjà soumis ---
console.log('Préparation du scénario SOC-DEMO…')
execSync(`"${PY}" -m benchmark.importer_scenario --reinitialiser`, { cwd: AI, env: ENV })
const periode = new Date().toISOString().slice(0, 7)
const e1 = await jeton('expert1', 'expert1123')
const liste = await appel(e1.token, 'GET', `/masse/paiements?clientId=SOC-DEMO&periode=${periode}`)
const ids = liste.paiements.filter((p) => !p.enErreur).map((p) => p.id)
const xsds = await appel(e1.token, 'GET', '/masse/xsd')
const xsd = xsds.find((x) => x.compatible)
const dateExec = new Date(Date.now() + 3 * 86400000).toISOString().slice(0, 10)
const propre = await appel(e1.token, 'POST', '/masse/controle', { xsdSchemaId: xsd.id, clientId: 'SOC-DEMO', periode, matricules: [], transactionIds: ids, nomDebiteur: null, ibanDebiteur: null, bicDebiteur: null, dateExecution: dateExec, statuts: ['APPROUVEE'], exclureErreurs: false })
console.log('XSD retenu :', xsd && xsd.id, xsd && xsd.nom, '| fichier :', JSON.stringify(propre).slice(0, 300))
const soumis = await appel(e1.token, 'POST', `/masse/fichiers/${propre.id}/soumettre`)
console.log('Fichier propre', propre.id, 'statut après soumission :', soumis.statutFichier || soumis.statut || JSON.stringify(soumis).slice(0, 200))
const manager = await jeton('manager1', 'manager1123')
await appel(manager.token, 'POST', '/anomalies/detecter')

const navigateur = await puppeteer.launch({
  executablePath: EDGE, headless: false, defaultViewport: null,
  args: ['--start-maximized', '--no-sandbox', '--use-fake-device-for-media-stream', '--use-fake-ui-for-media-stream'],
})
const page = (await navigateur.pages())[0]
page.setDefaultTimeout(30000)

async function bandeau(titre, texte) {
  await page.evaluate((t, x, h) => {
    let b = document.getElementById('demo-bandeau')
    if (!b) {
      b = document.createElement('div')
      b.id = 'demo-bandeau'
      b.style.cssText = 'position:fixed;left:0;right:0;bottom:0;z-index:99999;background:rgba(31,58,95,.95);color:#fff;padding:10px 24px;font:16px Segoe UI,sans-serif;pointer-events:none'
      document.body.appendChild(b)
    }
    b.innerHTML = `<b style="color:#8fd3ff">${h} — ${t}</b> &nbsp; ${x}`
  }, titre, texte, horloge())
  console.log(`[${horloge()}] ${titre} — ${texte}`)
}
const clic = async (selecteur) => { await page.locator(selecteur).click() }
const menu = (libelle) => clic(`.ant-menu-item::-p-text(${libelle})`)
const photo = (nom) => page.screenshot({ path: join(SORTIE, nom) })
async function etape(titre, texte, action) {
  try { await bandeau(titre, texte); await action(); await bandeau(titre, texte) } catch (e) { console.log(`  ! ${titre} : ${e.message}`); await photo(`erreur_${horloge().replace(':', '-')}.png`).catch(() => {}) }
}
async function ouvrirSelect(index, libelleOption) {
  const selects = await page.$$('.ant-select')
  await selects[index].click()
  await pause(500)
  const options = '.ant-select-dropdown:not(.ant-select-dropdown-hidden) .ant-select-item-option:not(.ant-select-item-option-disabled)'
  await page.waitForSelector(options)
  if (libelleOption) await page.locator(`${options}::-p-text(${libelleOption})`).click()
  else await (await page.$(options)).click()
  await pause(600)
}
async function remplir(nom, valeur) {
  await page.$eval(`input[name=${nom}]`, (e, v) => {
    Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set.call(e, v)
    e.dispatchEvent(new Event('input', { bubbles: true }))
  }, valeur)
}
async function saisirFiche(employe, nom, iban, montant) {
  await page.waitForSelector('select[name=clientId] option[value="SOC-DEMO"]')
  await page.select('select[name=clientId]', 'SOC-DEMO')
  await remplir('employeId', employe)
  await remplir('nomBeneficiaire', nom)
  await remplir('iban', iban)
  await remplir('montant', montant)
  await pause(1500)
}
async function seConnecter(utilisateur, mdp) {
  await page.goto(WEB + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.reload({ waitUntil: 'networkidle0' })
  await page.type('input:not([type=password])', utilisateur, { delay: 15 })
  await page.type('input[type=password]', mdp, { delay: 15 })
  await clic('button::-p-text(Se connecter)')
  await page.waitForSelector('input[placeholder="123456"]')
  await page.type('input[placeholder="123456"]', await codeOtp(), { delay: 15 })
  await clic('button::-p-text(Vérifier)')
  await page.waitForSelector('.app-shell')
  await pause(1200)
}

debut = Date.now()

// 0:00 — Authentification : inscription (visage) et connexion par mot de passe + code à usage unique
await page.goto(WEB + '/register', { waitUntil: 'networkidle0' })
await bandeau('Authentification', 'Inscription avec photo du visage ; le compte doit être approuvé par un administrateur.')
await pause(5000)
await photo('01_inscription.png')

await etape('Authentification', 'Code OTP envoyé par e-mail (MailHog) ; connexion par reconnaissance faciale possible en alternative.', async () => {
  await page.goto(WEB + '/login', { waitUntil: 'networkidle0' })
  await page.evaluate(() => localStorage.clear())
  await page.reload({ waitUntil: 'networkidle0' })
  await page.type('input:not([type=password])', 'expert1', { delay: 60 })
  await page.type('input[type=password]', 'expert1123', { delay: 60 })
  await photo('02_login.png')
  await clic('button::-p-text(Se connecter)')
  await page.waitForSelector('input[placeholder="123456"]')
  await pause(1500)
  await photo('03_otp.png')
  await page.type('input[placeholder="123456"]', await codeOtp(), { delay: 80 })
  await clic('button::-p-text(Vérifier)')
  await page.waitForSelector('.app-shell')
  await pause(1500)
})

// 0:35 — Expert RH : tableau de bord et menus limités à son rôle
await etape('Expert RH', 'Tableau de bord opérationnel ; le menu et les données dépendent du rôle et des sociétés affectées.', async () => {
  await pause(4000)
  await photo('04_dashboard_expert.png')
})

// 0:45 — Transactions : saisie contrôlée (IBAN modulo 97), contrôles métier, IA
await etape('Saisie des fiches', 'Une fiche avec un IBAN invalide est enregistrée en brouillon : elle ne pourra pas être approuvée.', async () => {
  await menu('Transactions')
  await saisirFiche('EMP-DEMO1', 'Karim Mansour', 'FR7630006000011234567890188', '1800')
  await photo('05_iban_invalide_saisie.png')
  await clic('button::-p-text(Ajouter)')
  await pause(2500)
})
await etape('Saisie des fiches', 'Seconde fiche, IBAN valide : statut « Non approuvé » en attendant le Manager.', async () => {
  await saisirFiche('EMP-DEMO2', 'Salma Ben Ali', 'FR7630006000011234567890189', '2450')
  await clic('button::-p-text(Ajouter)')
  await pause(2500)
  await photo('06_fiche_creee.png')
})
await etape('Contrôles', 'Contrôles métier, puis analyse IA (Isolation Forest) : montants suspects avec raison et recommandation.', async () => {
  await clic('button::-p-text(Contrôles métier)')
  await pause(2500)
  await clic('button::-p-text(Analyser (IA))')
  await page.waitForSelector('.ia-alert', { timeout: 60000 })
  await pause(4000)
  await photo('07_analyse_ia.png')
})

// 1:45 — Anomalies
await etape('Anomalies', 'Registre des anomalies : détail des raisons, « Marquer corrigée » ou « Ignorer ».', async () => {
  await menu('Anomalies')
  await pause(2500)
  const lien = await page.$('button.link-btn')
  if (lien) await lien.click()
  await pause(2500)
  await photo('08_anomalies.png')
  await clic('button::-p-text(Marquer corrigée)')
  await pause(2000)
})

// 2:10 — Virement de masse : préparation puis contrôle XSD + métier + IA
await etape('Virement de masse', 'Société, période, schéma XSD, puis tous les salariés approuvés (1 100 paiements).', async () => {
  await menu('Virements de masse')
  await page.waitForSelector('.ant-select')
  await ouvrirSelect(0, 'SOC-DEMO')
  await ouvrirSelect(1)
  await ouvrirSelect(2)
  await pause(1500)
  await clic('button::-p-text(Tout sélectionner)')
  await pause(1500)
  await photo('09_preparation.png')
})
await etape('Virement de masse', 'Lancement : contrôles métier, analyse IA, génération pain.001 et validation XSD en quelques secondes.', async () => {
  await clic('button::-p-text(Lancer le contrôle et générer)')
  await clic('.ant-modal-confirm-btns .ant-btn-primary, .ant-modal-footer .ant-btn-primary')
  await page.waitForFunction(() => location.pathname.includes('/resultat/'), { timeout: 90000 })
  await pause(3000)
  await photo('10_synthese.png')
})
for (const [onglet, texte, nom] of [
  ['XML', 'XML SEPA généré, coloré et navigable.', '11_xml.png'],
  ['Validation XSD', 'Conformité au schéma XSD : diagnostics par ligne.', '12_xsd.png'],
  ['Contrôle IA', 'Salariés suspects avec explication et recommandation ; l’IA ne bloque jamais.', '13_ia.png'],
  ['Contrôles métier', 'Erreurs métier : elles bloquent la soumission jusqu’à correction ou exclusion.', '14_metier.png'],
]) {
  await etape('Résultat du contrôle', texte, async () => { await clic(`.ant-tabs-tab::-p-text(${onglet})`); await pause(3500); await photo(nom) })
}

// 3:15 — Manager : quatre yeux
await etape('Manager', 'Changement de rôle : le Manager valide ce que l’Expert RH prépare (principe des quatre yeux).', async () => {
  await seConnecter('manager1', 'manager1123')
  await menu('Transactions')
  await pause(2500)
})
const cliquerLigne = async (texteLigne) => {
  await page.waitForFunction((t) => [...document.querySelectorAll('tbody tr')].some((tr) => tr.textContent.includes(t)), {}, texteLigne)
  const ligne = await page.evaluateHandle((t) => [...document.querySelectorAll('tbody tr')].find((tr) => tr.textContent.includes(t)), texteLigne)
  await (await ligne.asElement().$('button')).click()
}
await etape('Manager', 'Il tente d’approuver la fiche à IBAN invalide : refus ; la fiche reste en brouillon.', async () => {
  await cliquerLigne('EMP-DEMO1')
  await pause(3000)
  await remonter()
  await pause(2000)
  await photo('15a_approbation_refusee.png')
})
await etape('Manager', 'Il approuve la fiche valide : seules les fiches approuvées entrent dans un virement.', async () => {
  await cliquerLigne('EMP-DEMO2')
  await pause(2500)
  await remonter()
  await photo('15b_manager_approbation.png')
})
await etape('Manager', 'Fichiers à valider : examen du résultat, rejet avec motif obligatoire, validation.', async () => {
  await menu('Fichiers à valider')
  await pause(2500)
  await cliquerLigne('SOC-DEMO')
  await page.waitForFunction(() => location.pathname.includes('/resultat/'))
  await pause(3500)
  await clic('button::-p-text(Rejeter)')
  await pause(2500)
  await photo('16_rejet_motif.png')
  await clic('.ant-modal-footer button::-p-text(Annuler)')
  await pause(800)
  await clic('button::-p-text(Valider)')
  await clic('.ant-modal-confirm-btns .ant-btn-primary')
  await pause(3000)
  await photo('17_valide.png')
})
await etape('Manager', 'Téléchargement : préfixe NON_VALIDE avant validation, nom normal après. Affectation des sociétés aux experts.', async () => {
  await menu('Affectation des sociétés')
  await pause(3500)
  await photo('18_affectations.png')
})
await etape('Pilotage', 'Indicateurs KPI par rôle : volumes, montants, anomalies, taux de validation.', async () => {
  await menu('Indicateurs KPI')
  await pause(4000)
  await photo('19_kpi.png')
})

// 4:00 — Administrateur : gouvernance sans accès aux données individuelles de paie
await etape('Administrateur', 'L’administrateur gère comptes, sociétés et audit ; il n’accède à aucune donnée de paie individuelle.', async () => {
  await seConnecter('admin', 'admin123')
  await menu('Utilisateurs')
  await pause(3500)
  await photo('20_utilisateurs.png')
})
await etape('Administrateur', 'Sociétés débitrices (IBAN masqué), schémas XSD et supervision de l’historique.', async () => {
  await menu('Sociétés débitrices')
  await pause(2500)
  await menu('Schémas XSD')
  await pause(2500)
  await menu('Historique SEPA')
  await pause(3000)
  await photo('21_supervision.png')
})
await etape('Administrateur', 'Journal d’audit : chaque action sensible est tracée (qui, quoi, quand).', async () => {
  await menu("Journal d'audit")
  await pause(4000)
  await photo('22_audit.png')
})
await etape('Conclusion', 'API REST documentée (Swagger), 178 tests Maven, 27 tests pytest, recette 40/40.', async () => {
  await page.goto('http://localhost:8080/swagger-ui.html', { waitUntil: 'networkidle0' })
  await pause(4000)
})

console.log(`Démo terminée en ${horloge()}`)
await navigateur.close()
execSync(`"${PY}" -m benchmark.importer_scenario --retirer`, { cwd: AI, env: ENV })
