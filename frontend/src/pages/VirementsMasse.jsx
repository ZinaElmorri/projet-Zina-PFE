import { useEffect, useState } from 'react'
import api from '../api/client'
import XmlViewer from '../components/XmlViewer.jsx'
import XsdAnalyse from '../components/XsdAnalyse.jsx'
import './virements-masse.css'

const euro = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' })

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
  const [rechMatricule, setRechMatricule] = useState('')
  const [matriculesSel, setMatriculesSel] = useState([])

  const [paiements, setPaiements] = useState([])
  const [total, setTotal] = useState(0)
  const [selection, setSelection] = useState(new Set())
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)

  const [apercu, setApercu] = useState(null)
  const [ligneActive, setLigneActive] = useState(null)

  const [historique, setHistorique] = useState([])
  const [message, setMessage] = useState('')
  const [chargement, setChargement] = useState(false)

  useEffect(() => {
    api.get('/masse/xsd').then(({ data }) => setXsdList(data))
    chargerFiltres('', '')
    chargerHistorique()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

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
    setXsdId(id)
    setAnalyse(null)
    if (!id) return
    try {
      const { data } = await api.get(`/masse/xsd/${id}/analyse`)
      setAnalyse(data)
      setAnalyseOuverte(true)
    } catch {
      setMessage('Ce XSD n’est pas compatible pain.001 (CstmrCdtTrfInitn absent).')
    }
  }

  const rechercher = async () => {
    setMessage('')
    const params = {}
    if (clientId) params.clientId = clientId
    if (periode) params.periode = periode
    if (matriculesSel.length) params.matricules = matriculesSel.join(',')
    const { data } = await api.get('/masse/paiements', { params })
    setPaiements(data.paiements)
    setTotal(data.total)
    // Sélectionne par défaut les lignes sans erreur de contrôle.
    setSelection(new Set(data.paiements.filter((p) => !p.enErreur).map((p) => p.id)))
    setPage(0)
    setApercu(null)
    if (data.paiements.length === 0) setMessage('Aucun paiement trouvé pour ces filtres.')
  }

  const basculer = (id) => {
    const s = new Set(selection)
    s.has(id) ? s.delete(id) : s.add(id)
    setSelection(s)
  }

  const tousSelectionnes = paiements.length > 0 && selection.size === paiements.length

  const basculerTout = () => {
    setSelection(tousSelectionnes ? new Set() : new Set(paiements.map((p) => p.id)))
  }

  const corpsRequete = () => ({
    xsdSchemaId: Number(xsdId),
    clientId: clientId || null,
    periode: periode || null,
    matricules: matriculesSel,
    transactionIds: [...selection],
    nomDebiteur: debiteur.nom,
    ibanDebiteur: debiteur.iban,
    bicDebiteur: debiteur.bic,
    dateExecution,
    statuts: ['APPROUVEE'],
    exclureErreurs: false,
  })

  const genererApercu = async () => {
    if (!xsdId) { setMessage('Choisissez d’abord un XSD.'); return }
    if (selection.size === 0) { setMessage('Sélectionnez au moins un paiement.'); return }
    setChargement(true)
    setMessage('')
    try {
      const { data } = await api.post('/masse/apercu', corpsRequete())
      setApercu(data)
    } catch (e) {
      setMessage('Erreur lors de l’aperçu : ' + (e.response?.data?.erreur || e.message))
    } finally {
      setChargement(false)
    }
  }

  const enregistrer = async () => {
    if (!apercu) return
    if (!apercu.valideXsd && !window.confirm('Le XML est NON conforme au XSD. Enregistrer quand même ?')) return
    const { data } = await api.post('/masse/fichiers', corpsRequete())
    setMessage(`Fichier enregistré : ${data.nomFichier}`)
    chargerHistorique()
    telecharger(data.id)
  }

  const telecharger = async (id) => {
    const resp = await api.get(`/masse/fichiers/${id}/telecharger`, { responseType: 'blob' })
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
    const { data } = await api.get(`/masse/fichiers/${id}`)
    setApercu({
      xml: data.xml,
      valideXsd: data.resume.valideXsd,
      erreurs: data.erreurs,
      erreursControleMetier: [],
      nbTransactions: data.resume.nbTransactions,
      ctrlSum: data.resume.ctrlSum,
      versionMessage: data.resume.versionMessage,
      msgId: data.resume.nomFichier,
    })
    window.scrollTo({ top: document.body.scrollHeight, behavior: 'smooth' })
  }

  const copier = () => apercu && navigator.clipboard.writeText(apercu.xml)

  const matriculesFiltres = matriculesOpt.filter(
    (m) => !rechMatricule || (m.matricule + ' ' + (m.nom || '')).toLowerCase().includes(rechMatricule.toLowerCase())
  )

  const totalPages = Math.max(1, Math.ceil(paiements.length / pageSize))
  const pageSafe = Math.min(page, totalPages - 1)
  const paiementsPage = paiements.slice(pageSafe * pageSize, pageSafe * pageSize + pageSize)

  return (
    <div>
      <h2>Virements de masse SEPA</h2>
      {message && <div className="info">{message}</div>}

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
          <label>Société émettrice <input value={debiteur.nom} onChange={(e) => setDebiteur({ ...debiteur, nom: e.target.value })} /></label>
          <label>IBAN débiteur <input value={debiteur.iban} onChange={(e) => setDebiteur({ ...debiteur, iban: e.target.value })} /></label>
          <label>BIC débiteur <input value={debiteur.bic} onChange={(e) => setDebiteur({ ...debiteur, bic: e.target.value })} /></label>
          <label>Date d’exécution <input type="date" value={dateExecution} onChange={(e) => setDateExecution(e.target.value)} /></label>
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
            <select value={clientId} onChange={(e) => { setClientId(e.target.value); chargerFiltres(e.target.value, periode) }}>
              <option value="">Toutes</option>
              {societes.map((s) => <option key={s} value={s}>{s}</option>)}
            </select>
          </label>
          <label>Période de paie
            <select value={periode} onChange={(e) => { setPeriode(e.target.value); chargerFiltres(clientId, e.target.value); setMatriculesSel([]) }}>
              <option value="">— choisir —</option>
              {periodes.map((p) => <option key={p} value={p}>{p}</option>)}
            </select>
          </label>
          <button onClick={rechercher}>Rechercher</button>
        </div>

        {periode && matriculesOpt.length > 0 && (
          <div className="masse-matricules">
            <input placeholder="Filtrer matricule…" value={rechMatricule} onChange={(e) => setRechMatricule(e.target.value)} />
            <div className="masse-matricules-liste">
              {matriculesFiltres.map((m) => (
                <label key={m.matricule} className="masse-chip">
                  <input type="checkbox" checked={matriculesSel.includes(m.matricule)}
                    onChange={(e) => setMatriculesSel(e.target.checked
                      ? [...matriculesSel, m.matricule]
                      : matriculesSel.filter((x) => x !== m.matricule))} />
                  {m.matricule} — {m.nom}
                </label>
              ))}
            </div>
            <small className="muted">Aucun matricule coché = tous les salariés de la société sur la période.</small>
          </div>
        )}

        {paiements.length > 0 && (
          <>
            <div className="masse-table-bar">
              <p>
                <strong>{selection.size}</strong> / {paiements.length} paiement(s) sélectionné(s) — Total {euro.format(total)}
              </p>
              <label className="masse-pagesize">
                Lignes par page
                <select value={pageSize} onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0) }}>
                  <option value={10}>10</option>
                  <option value={20}>20</option>
                  <option value={50}>50</option>
                </select>
              </label>
            </div>
            <table>
              <thead>
                <tr>
                  <th>
                    <input
                      type="checkbox"
                      checked={tousSelectionnes}
                      onChange={basculerTout}
                      title="Tout sélectionner / tout désélectionner"
                    />
                  </th>
                  <th>Matricule</th><th>Nom</th><th>IBAN</th><th>BIC</th><th>Montant</th><th>Statut</th>
                </tr>
              </thead>
              <tbody>
                {paiementsPage.map((p) => (
                  <tr key={p.id} className={p.enErreur ? 'row-suspect' : undefined}>
                    <td><input type="checkbox" checked={selection.has(p.id)} onChange={() => basculer(p.id)} /></td>
                    <td>{p.matricule}</td>
                    <td>{p.nomBeneficiaire}</td>
                    <td>{p.ibanMasque}</td>
                    <td>{p.bic || '—'}</td>
                    <td>{euro.format(p.montant)}</td>
                    <td>{p.statut}{p.enErreur ? ` ⚠️ ${p.erreurControle}` : ''}</td>
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
          <button onClick={genererApercu} disabled={chargement}>Aperçu</button>
          <button className="secondary" onClick={copier} disabled={!apercu}>Copier</button>
          <button onClick={enregistrer} disabled={!apercu}>Générer et enregistrer</button>
          {chargement && <span className="spinner" />}
        </div>

        {apercu && (
          <>
            {apercu.valideXsd
              ? <div className="ia-alert ia-alert--ok">✅ Conforme au XSD ({apercu.versionMessage}) — {apercu.nbTransactions} transaction(s), CtrlSum {apercu.ctrlSum}</div>
              : <div className="ia-alert ia-alert--warn">❌ Non conforme — {apercu.erreurs.length} erreur(s)</div>}

            {apercu.erreursControleMetier && apercu.erreursControleMetier.length > 0 && (
              <div className="ia-alert ia-alert--off">
                Contrôles métier : {apercu.erreursControleMetier.length} avertissement(s)
                <ul>{apercu.erreursControleMetier.slice(0, 10).map((e, i) => <li key={i}>{e}</li>)}</ul>
              </div>
            )}

            {apercu.erreurs.length > 0 && (
              <table className="masse-erreurs">
                <thead><tr><th>Ligne</th><th>Col.</th><th>Élément</th><th>Message</th></tr></thead>
                <tbody>
                  {apercu.erreurs.map((e, i) => (
                    <tr key={i} className="masse-erreur-ligne" onClick={() => setLigneActive(e.ligne)}>
                      <td>{e.ligne}</td><td>{e.colonne}</td><td>{e.element || '—'}</td><td>{e.message}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}

            <XmlViewer xml={apercu.xml} erreurs={apercu.erreurs} ligneActive={ligneActive} />
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
                  <button onClick={() => telecharger(f.id)}>Retélécharger</button>
                </td>
              </tr>
            ))}
            {historique.length === 0 && <tr><td colSpan="10" className="muted">Aucun fichier généré.</td></tr>}
          </tbody>
        </table>
      </section>
    </div>
  )
}
