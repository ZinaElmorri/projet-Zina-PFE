import { useEffect, useRef, useState } from 'react'
import api from '../api/client'
import XmlViewer from '../components/XmlViewer.jsx'
import XsdAnalyse from '../components/XsdAnalyse.jsx'
import './virements-masse.css'

const euro = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' })

const messageEchec = (erreur) => {
  if (erreur.response?.status === 401) return 'Votre session a expiré. Reconnectez-vous pour continuer.'
  if (erreur.response?.status === 403) return "Votre compte n'a pas les droits nécessaires pour cette opération. Contactez un administrateur."
  if (erreur.response?.status >= 500) return 'Le serveur a rencontré un problème. Réessayez plus tard ; si le problème persiste, contactez un administrateur.'
  if (typeof erreur.response?.data?.erreur === 'string') return erreur.response.data.erreur
  if (erreur.isAxiosError) return erreur.response
    ? 'La demande a été refusée. Vérifiez les paramètres et les données sélectionnées, puis réessayez.'
    : 'Le serveur est injoignable. Vérifiez votre connexion et la disponibilité du service, puis réessayez.'
  return erreur.message || "L'opération a échoué. Réessayez ; si le problème persiste, contactez un administrateur."
}

// Aujourd'hui + 2 jours ouvrés.
const dateExecutionDefaut = () => {
  const d = new Date()
  let ajoutes = 0
  while (ajoutes < 2) {
    d.setDate(d.getDate() + 1)
    const j = d.getDay()
    if (j !== 0 && j !== 6) ajoutes++
  }
  return d.toISOString().slice(0, 10)
}

export default function VirementsMasse() {
  const [xsdList, setXsdList] = useState([])
  const [xsdId, setXsdId] = useState('')
  const [analyse, setAnalyse] = useState(null)
  const [analyseOuverte, setAnalyseOuverte] = useState(false)

  const [debiteur, setDebiteur] = useState({ nom: '', iban: '', bic: '' })
  const [dateExecution, setDateExecution] = useState(dateExecutionDefaut())

  const [societes, setSocietes] = useState([])
  const [periodes, setPeriodes] = useState([])
  const [matriculesOpt, setMatriculesOpt] = useState([])
  const [clientId, setClientId] = useState('')
  const [periode, setPeriode] = useState('')
  const [filtreMatricule, setFiltreMatricule] = useState('')
  const [matriculesSel, setMatriculesSel] = useState([])

  const [paiements, setPaiements] = useState([])
  const [total, setTotal] = useState(0)
  const [selection, setSelection] = useState(new Set())
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  const [apercu, setApercu] = useState(null)
  const [requeteApercu, setRequeteApercu] = useState(null)
  const revision = useRef(0)
  const operationEnCours = useRef(false)
  const [ligneActive, setLigneActive] = useState(null)

  const [historique, setHistorique] = useState([])
  const [message, setMessage] = useState('')
  const [chargement, setChargement] = useState(false)
  const [controleIa, setControleIa] = useState(null)
  const [analyseIaEnCours, setAnalyseIaEnCours] = useState(false)

  useEffect(() => {
    api.get('/masse/xsd').then(({ data }) => setXsdList(data))
    chargerFiltres('', '')
    chargerHistorique()
    return () => { revision.current++ }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const invaliderApercu = () => {
    revision.current++
    setApercu(null)
    setRequeteApercu(null)
    setLigneActive(null)
  }

  const modifierParametres = (modifier) => {
    invaliderApercu()
    modifier()
  }

  const chargerFiltres = (cid, per) => {
    const params = {}
    if (cid) params.clientId = cid
    if (per) params.periode = per
    api.get('/masse/filtres', { params }).then(({ data }) => {
      setSocietes(data.societes)
      setPeriodes(data.periodes)
      setMatriculesOpt(data.matricules)
    })
  }

  const chargerHistorique = () => api.get('/masse/fichiers').then(({ data }) => setHistorique(data))

  const choisirXsd = async (id) => {
    invaliderApercu()
    setXsdId(id)
    setAnalyse(null)
    if (!id) return
    try {
      const { data } = await api.get(`/masse/xsd/${id}/analyse`)
      setAnalyse(data)
      setAnalyseOuverte(true)
    } catch (e) {
      setMessage('Analyse du schéma impossible : ' + messageEchec(e))
    }
  }

  const rechercher = async () => {
    if (operationEnCours.current) return
    operationEnCours.current = true
    setChargement(true)
    invaliderApercu()
    setMessage('')
    const params = {}
    if (clientId) params.clientId = clientId
    if (periode) params.periode = periode
    if (matriculesSel.length) params.matricules = matriculesSel.join(',')
    setControleIa(null)
    try {
      const { data } = await api.get('/masse/paiements', { params })
      setPaiements(data.paiements)
      setTotal(data.total)
      // Sélectionne par défaut les lignes sans erreur de contrôle.
      setSelection(new Set(data.paiements.filter((p) => !p.enErreur).map((p) => p.id)))
      setPage(0)
      if (data.paiements.length === 0) setMessage('Aucun paiement ne correspond à la société et à la période sélectionnées. Vérifiez ces filtres et la disponibilité des paiements approuvés.')
    } catch (e) {
      setMessage('Recherche des paiements impossible : ' + messageEchec(e))
    } finally {
      operationEnCours.current = false
      setChargement(false)
    }
  }

  const analyserIa = async () => {
    if (operationEnCours.current) return
    operationEnCours.current = true
    setAnalyseIaEnCours(true)
    setMessage('')
    const params = {}
    if (clientId) params.clientId = clientId
    if (periode) params.periode = periode
    if (matriculesSel.length) params.matricules = matriculesSel.join(',')
    try {
      const { data } = await api.post('/masse/analyse-ia', null, { params })
      setPaiements(data.paiements.paiements)
      setTotal(data.paiements.total)
      setControleIa(data)
      invaliderApercu()
    } catch (e) {
      setMessage('Contrôle IA impossible : ' + messageEchec(e))
    } finally {
      operationEnCours.current = false
      setAnalyseIaEnCours(false)
    }
  }

  const basculer = (id) => {
    const s = new Set(selection)
    s.has(id) ? s.delete(id) : s.add(id)
    invaliderApercu()
    setSelection(s)
  }

  const lignesVisibles = () => paiements.filter(
    (p) => !filtreMatricule ||
      (p.matricule + ' ' + (p.nomBeneficiaire || '')).toLowerCase().includes(filtreMatricule.toLowerCase())
  )

  const tousSelectionnes = (() => {
    const vis = lignesVisibles()
    return vis.length > 0 && vis.every((p) => selection.has(p.id))
  })()

  const selectionnerTous = () => {
    const s = new Set(selection)
    lignesVisibles().forEach((p) => s.add(p.id))
    invaliderApercu()
    setSelection(s)
  }

  const deselectionnerTous = () => {
    const s = new Set(selection)
    lignesVisibles().forEach((p) => s.delete(p.id))
    invaliderApercu()
    setSelection(s)
  }

  const corpsRequete = () => ({
    xsdSchemaId: Number(xsdId),
    clientId: clientId || null,
    periode: periode || null,
    matricules: matriculesSel,
    transactionIds: [...selection].sort((premier, second) => premier - second),
    nomDebiteur: debiteur.nom,
    ibanDebiteur: debiteur.iban,
    bicDebiteur: debiteur.bic,
    dateExecution,
    statuts: ['APPROUVEE'],
    exclureErreurs: false,
  })

  const genererApercu = async () => {
    if (operationEnCours.current) return
    if (!xsdId) { setMessage('Aucun schéma XSD sélectionné. Choisissez un schéma pain.001 compatible pour générer et valider le fichier SEPA.'); return }
    if (selection.size === 0) { setMessage('Aucun paiement sélectionné. Cochez au moins un paiement dans la liste avant de générer le fichier SEPA.'); return }
    operationEnCours.current = true
    invaliderApercu()
    const revisionRequete = revision.current
    const requete = corpsRequete()
    setChargement(true)
    setMessage('')
    try {
      const { data } = await api.post('/masse/apercu', requete)
      if (revisionRequete !== revision.current) return
      setApercu(data)
      setRequeteApercu(requete)
    } catch (e) {
      setMessage('Génération de l’aperçu impossible : ' + messageEchec(e))
    } finally {
      operationEnCours.current = false
      setChargement(false)
    }
  }

  const peutEnregistrer = apercu?.valideXsd === true && requeteApercu !== null &&
    JSON.stringify(requeteApercu) === JSON.stringify(corpsRequete())

  const enregistrer = async () => {
    if (operationEnCours.current || !peutEnregistrer) return
    operationEnCours.current = true
    setChargement(true)
    setMessage('')
    try {
      const { data } = await api.post('/masse/fichiers', requeteApercu)
      setRequeteApercu(null)
      const messages = [`Fichier enregistré : ${data.nomFichier}`]
      try {
        if (data.valideXsd !== true) throw new Error('Le fichier enregistré ne respecte pas le schéma XSD. Consultez ses erreurs et générez un nouveau fichier conforme avant de le télécharger.')
        await telecharger(data.id)
      } catch (e) {
        messages.push('Le fichier est enregistré, mais le téléchargement a échoué : ' + messageEchec(e))
      }
      try {
        await chargerHistorique()
      } catch {
        messages.push("Le fichier est enregistré, mais l'historique n'a pas pu être actualisé. Actualisez la page avant de rechercher ce fichier ; ne l'enregistrez pas une deuxième fois.")
      }
      setMessage(messages.join(' '))
    } catch (e) {
      invaliderApercu()
      setMessage('Enregistrement du fichier impossible : ' + messageEchec(e))
    } finally {
      operationEnCours.current = false
      setChargement(false)
    }
  }

  const telecharger = async (id) => {
    let resp
    try {
      resp = await api.get(`/masse/fichiers/${id}/telecharger`, { responseType: 'blob' })
    } catch (e) {
      if (e.response?.data instanceof Blob) {
        let erreur
        try { erreur = JSON.parse(await e.response.data.text()).erreur } catch { erreur = null }
        if (erreur) throw new Error(erreur)
      }
      throw e
    }
    const cd = resp.headers['content-disposition'] || ''
    const m = cd.match(/filename="?([^"]+)"?/)
    const nom = m ? m[1] : `SEPA_${id}.xml`
    const url = window.URL.createObjectURL(new Blob([resp.data], { type: 'application/xml' }))
    const a = document.createElement('a')
    a.href = url
    a.download = nom
    a.click()
    window.URL.revokeObjectURL(url)
  }

  const revoir = async (id) => {
    if (operationEnCours.current) return
    operationEnCours.current = true
    setChargement(true)
    invaliderApercu()
    setMessage('')
    try {
      const { data } = await api.get(`/masse/fichiers/${id}`)
      setApercu({
        xml: data.xml,
        valideXsd: data.resume.valideXsd,
        erreurs: data.erreurs,
        erreursControleMetier: [],
        avertissementsControleMetier: [],
        avertissements: [],
        erreursControleMetierXml: [],
        nbTransactions: data.resume.nbTransactions,
        ctrlSum: data.resume.ctrlSum,
        versionMessage: data.resume.versionMessage,
        msgId: data.resume.nomFichier,
      })
      window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' })
    } catch (e) {
      setMessage('Consultation du fichier impossible : ' + messageEchec(e))
    } finally {
      operationEnCours.current = false
      setChargement(false)
    }
  }

  const retelecharger = async (fichier) => {
    if (operationEnCours.current || !fichier.valideXsd) return
    operationEnCours.current = true
    setChargement(true)
    setMessage('')
    try {
      await telecharger(fichier.id)
    } catch (e) {
      setMessage('Téléchargement du fichier impossible : ' + messageEchec(e))
    } finally {
      operationEnCours.current = false
      setChargement(false)
    }
  }

  const copier = () => apercu && navigator.clipboard.writeText(apercu.xml)

  const paiementsFiltres = lignesVisibles()

  const totalPages = Math.max(1, Math.ceil(paiementsFiltres.length / pageSize))
  const pageSafe = Math.min(page, totalPages - 1)
  const paiementsPage = paiementsFiltres.slice(pageSafe * pageSize, pageSafe * pageSize + pageSize)
  const diagnostics = apercu ? [
    ...apercu.erreurs.map((erreur) => ({ ...erreur, avertissement: false })),
    ...(apercu.avertissements || []).map((avertissement) => ({ ...avertissement, avertissement: true })),
  ] : []

  return (
    <div>
      <h2>Virements de masse SEPA</h2>
      {message && <div className="info">{message}</div>}
      <fieldset disabled={chargement} style={{ border: 0, padding: 0, margin: 0, minWidth: 0 }}>

      {/* Paramètres */}
      <section className="masse-bloc">
        <h3>Paramètres</h3>
        <div className="inline-form">
          <label>Schéma XSD
            <select value={xsdId} onChange={(e) => choisirXsd(e.target.value)}>
              <option value="">— choisir —</option>
              {xsdList.map((x) => (
                <option key={x.id} value={x.id} disabled={!x.compatible}>
                  {x.nom} {x.versionMessage ? `(${x.versionMessage})` : ''}{x.compatible ? '' : ' — non compatible'}
                </option>
              ))}
            </select>
          </label>
          <label>Société émettrice <input value={debiteur.nom} onChange={(e) => modifierParametres(() => setDebiteur({ ...debiteur, nom: e.target.value }))} /></label>
          <label>IBAN débiteur <input value={debiteur.iban} onChange={(e) => modifierParametres(() => setDebiteur({ ...debiteur, iban: e.target.value }))} /></label>
          <label>BIC débiteur <input value={debiteur.bic} onChange={(e) => modifierParametres(() => setDebiteur({ ...debiteur, bic: e.target.value }))} /></label>
          <label>Date d’exécution <input type="date" value={dateExecution} onChange={(e) => modifierParametres(() => setDateExecution(e.target.value))} /></label>
        </div>
        {xsdId && (
          <div className="masse-analyse">
            <button className="secondary" onClick={() => setAnalyseOuverte(!analyseOuverte)}>
              {analyseOuverte ? '▾' : '▸'} Analyse du XSD
            </button>
            {analyseOuverte && <XsdAnalyse analyse={analyse} />}
          </div>
        )}
      </section>

      {/* Filtres et génération */}
      <section className="masse-bloc">
        <h3>Filtres et génération</h3>
        <div className="inline-form">
          <label>Société
            <select value={clientId} onChange={(e) => modifierParametres(() => { setClientId(e.target.value); chargerFiltres(e.target.value, periode) })}>
              <option value="">Toutes</option>
              {societes.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
          <label>Période de paie
            <select value={periode} onChange={(e) => modifierParametres(() => { setPeriode(e.target.value); chargerFiltres(clientId, e.target.value); setMatriculesSel([]) })}>
              <option value="">— choisir —</option>
              {periodes.map((p) => <option key={p} value={p}>{p}</option>)}
            </select>
          </label>
          <button onClick={rechercher}>Rechercher</button>
        </div>

        {paiements.length > 0 && (
          <>
            <div className="masse-table-bar">
              <p>
                <strong>{selection.size}</strong> / {paiements.length} paiement(s) sélectionné(s) — Total {euro.format(total)}
              </p>
              <div className="masse-table-actions">
                <input
                  placeholder="Saisir / filtrer un matricule…"
                  value={filtreMatricule}
                  onChange={(e) => { setFiltreMatricule(e.target.value); setPage(0) }}
                />
                <button type="button" onClick={selectionnerTous}>Tous</button>
                <button type="button" className="secondary" onClick={deselectionnerTous}>Aucun</button>
                <button type="button" onClick={analyserIa} disabled={analyseIaEnCours || chargement}
                        title="Détecte les montants inhabituels (modèle Isolation Forest) sans bloquer la génération">
                  {analyseIaEnCours ? 'Contrôle IA en cours…' : 'Contrôle IA des montants'}
                </button>
                <label className="masse-pagesize">
                  Lignes par page
                  <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0) }}>
                    <option value={10}>10</option>
                    <option value={20}>20</option>
                    <option value={50}>50</option>
                  </select>
                </label>
              </div>
            </div>
            {controleIa && (
              !controleIa.disponible
                ? <div className="ia-alert ia-alert--off">Contrôle IA indisponible pour le moment : la génération reste possible, les contrôles métier s'appliquent normalement.</div>
                : controleIa.nbSuspects > 0
                  ? <div className="ia-alert ia-alert--warn">
                      Contrôle IA : <strong>{controleIa.nbSuspects}</strong> montant(s) suspect(s) sur {controleIa.nbAnalyses} paiement(s) analysé(s)
                      <span className="ia-alert__model"> — modèle {controleIa.modelVersion}</span>.
                      <p className="ia-alert__hint">Vérifiez ces paiements (colonne IA) avant de générer : ils resteront signalés en avertissement dans l’aperçu, sans bloquer le fichier.</p>
                    </div>
                  : <div className="ia-alert ia-alert--ok">Contrôle IA : aucun montant suspect sur {controleIa.nbAnalyses} paiement(s) analysé(s) <span className="ia-alert__model">— modèle {controleIa.modelVersion}</span>.</div>
            )}
            <table>
              <thead>
                <tr>
                  <th>
                    <input
                      type="checkbox"
                      checked={tousSelectionnes}
                      onChange={() => (tousSelectionnes ? deselectionnerTous() : selectionnerTous())}
                      title="Tout sélectionner / tout désélectionner"
                    />
                  </th>
                  <th>Matricule</th><th>Nom</th><th>IBAN</th><th>BIC</th><th>Montant</th><th>Statut</th><th>IA</th>
                </tr>
              </thead>
              <tbody>
                {paiementsPage.map((p) => (
                  <tr key={p.id} className={p.enErreur || p.suspectIa ? 'row-suspect' : undefined}>
                    <td><input type="checkbox" checked={selection.has(p.id)} onChange={() => basculer(p.id)} /></td>
                    <td>{p.matricule}</td>
                    <td>{p.nomBeneficiaire}</td>
                    <td>{p.ibanMasque}</td>
                    <td>{p.bic || '—'}</td>
                    <td>{euro.format(p.montant)}</td>
                    <td>{p.statut === 'BROUILLON' ? 'Non approuvé' : p.statut}{p.enErreur ? ` ⚠️ ${p.erreurControle}` : ''}</td>
                    <td>
                      {p.suspectIa
                        ? <span className="badge-suspect" title={p.raisonsIa || ''}>Suspect {p.scoreIa != null ? p.scoreIa.toFixed(2).replace('.', ',') : ''}</span>
                        : '—'}
                      {p.suspectIa && p.raisonsIa && <div className="ia-reco">{p.raisonsIa}</div>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {totalPages > 1 && (
              <div className="masse-pagination">
                <button className="secondary" onClick={() => setPage(pageSafe - 1)} disabled={pageSafe === 0}>‹ Précédent</button>
                <span>Page {pageSafe + 1} / {totalPages}</span>
                <button className="secondary" onClick={() => setPage(pageSafe + 1)} disabled={pageSafe >= totalPages - 1}>Suivant ›</button>
              </div>
            )}
          </>
        )}

        <div className="actions masse-generation">
          <button onClick={genererApercu} disabled={chargement}>Générer</button>
          <button className="secondary" onClick={copier} disabled={!apercu}>Copier</button>
          <button onClick={enregistrer} disabled={chargement || !peutEnregistrer}>Enregistrer et télécharger</button>
          {chargement && <span className="spinner" />}
        </div>

        {apercu && (
          <>
            {apercu.valideXsd
              ? <div className="ia-alert ia-alert--ok">✅ Conforme au schéma XSD ({apercu.versionMessage}) : {apercu.nbTransactions} virement(s), montant total {euro.format(apercu.ctrlSum)}</div>
              : <div className="ia-alert ia-alert--off">❌ XML non conforme au schéma XSD : {apercu.erreurs.length} erreur(s) à corriger avant l’enregistrement et le téléchargement.</div>}

            {apercu.erreursControleMetier && apercu.erreursControleMetier.length > 0 && (
              <div className="ia-alert ia-alert--off">
                Contrôles métier : {apercu.erreursControleMetier.length} erreur(s)
                <ul>{apercu.erreursControleMetier.slice(0, 10).map((e, i) => <li key={i}>{e}</li>)}</ul>
              </div>
            )}

            {apercu.avertissements?.length > 0 && (
              <div className="ia-alert ia-alert--warn">
                {apercu.avertissements.length} avertissement(s) non bloquant(s) pour la conformité XSD
                <ul>{apercu.avertissements.slice(0, 10).map((avertissement, index) => <li key={index}>{avertissement.message}</li>)}</ul>
              </div>
            )}

            {diagnostics.length > 0 && (
              <table className="masse-erreurs">
                <thead><tr><th>Ligne</th><th>Élément</th><th>Message</th></tr></thead>
                <tbody>
                  {diagnostics.map((diagnostic, index) => (
                    <tr key={index} className={`masse-erreur-ligne${diagnostic.avertissement ? ' masse-erreur-ligne--warn' : ''}`} onClick={() => setLigneActive(diagnostic.ligne)}>
                      <td>{diagnostic.ligne || '—'}</td><td>{diagnostic.element || '—'}</td><td>{diagnostic.avertissement ? 'Avertissement : ' : ''}{diagnostic.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}

            <XmlViewer
              xml={apercu.xml}
              erreurs={[...apercu.erreurs, ...(apercu.erreursControleMetierXml || [])]}
              avertissements={apercu.avertissements || []}
              ligneActive={ligneActive}
            />
          </>
        )}
      </section>

      {/* Historique */}
      <section className="masse-bloc">
        <h3>Historique des fichiers générés</h3>
        <table>
          <thead>
            <tr><th>Date</th><th>Fichier</th><th>Société</th><th>Période</th><th>Nb</th><th>Total</th><th>XSD</th><th>Statut</th><th>Par</th><th>Actions</th></tr>
          </thead>
          <tbody>
            {historique.map((f) => (
              <tr key={f.id}>
                <td>{(f.genereLe || '').replace('T', ' ').slice(0, 16)}</td>
                <td>{f.nomFichier}</td>
                <td>{f.clientId || '—'}</td>
                <td>{f.periode || '—'}</td>
                <td>{f.nbTransactions}</td>
                <td>{euro.format(f.ctrlSum || 0)}</td>
                <td>{f.xsdNom} {f.versionMessage ? `(${f.versionMessage})` : ''}</td>
                <td>{f.valideXsd ? '✅ Conforme' : `❌ ${f.nbErreurs} err.`}</td>
                <td>{f.generePar || '—'}</td>
                <td>
                  <button className="secondary" onClick={() => revoir(f.id)}>Revoir</button>
                  <button onClick={() => retelecharger(f)} disabled={!f.valideXsd || chargement}>Retélécharger</button>
                </td>
              </tr>
            ))}
            {historique.length === 0 && <tr><td colSpan="10" className="muted">Aucun fichier SEPA enregistré dans l’historique.</td></tr>}
          </tbody>
        </table>
      </section>
      </fieldset>
    </div>
  )
}
