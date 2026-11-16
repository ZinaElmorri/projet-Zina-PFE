import { useEffect, useState } from 'react'
import { Tag } from 'antd'
import api from '../api/client'
import ActionButton from '../components/ActionButton.jsx'
import { peutPreparer, peutValider } from '../constants/roles.js'
import { useAuth } from '../context/AuthContext.jsx'
import { formatMontant, masquerIban } from '../utils/format.js'

const empty = { employeId: '', clientId: '', nomBeneficiaire: '', iban: '', bic: '', montant: '', libelle: '' }

const STATUTS = {
  BROUILLON: { label: 'Non approuvé', color: 'default' },
  VALIDEE: { label: 'Validée', color: 'blue' },
  APPROUVEE: { label: 'Approuvée', color: 'green' },
  REJETEE: { label: 'Rejetée', color: 'red' },
}

// Affichage uniquement : le montant reste exprimé dans sa devise d'origine.
const afficherMontant = (t) => (!t.devise || t.devise === 'EUR' ? formatMontant(t.montant) : `${t.montant} ${t.devise}`)

export default function Transactions() {
  const { user } = useAuth()
  const [transactions, setTransactions] = useState([])
  const [form, setForm] = useState(empty)
  const [message, setMessage] = useState('')
  const [iaResult, setIaResult] = useState(null)
  const [approbationEnCours, setApprobationEnCours] = useState(null)
  const [societes, setSocietes] = useState([])

  const load = () => api.get('/transactions').then(({ data }) => setTransactions(data))

  useEffect(() => { load() }, [])

  // Sociétés proposées à la saisie : actives et accessibles à l'utilisateur (Expert RH : celles qui lui sont affectées).
  useEffect(() => {
    if (!peutPreparer(user?.role)) return
    Promise.all([api.get('/societes'), api.get('/masse/filtres')]).then(([{ data: toutes }, { data: filtres }]) => {
      const accessibles = new Set(filtres.societes)
      setSocietes(toutes.filter((s) => s.actif && accessibles.has(s.code)))
    })
  }, [user?.role])

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const handleCreate = async (e) => {
    e.preventDefault()
    setMessage('')
    try {
      await api.post('/transactions', { ...form, montant: parseFloat(form.montant) })
      setForm(empty)
      load()
    } catch (erreur) {
      setMessage(erreur.response?.data?.erreur || 'Ajout impossible : vérifiez les champs saisis et réessayez.')
    }
  }

  const approuver = async (id) => {
    if (approbationEnCours !== null) return
    setApprobationEnCours(id)
    setMessage('')
    try {
      await api.put(`/transactions/${id}/approuver`)
      setMessage(`Paiement n° ${id} approuvé.`)
      try {
        await load()
      } catch {
        setMessage(`Paiement n° ${id} approuvé, mais la liste n’a pas pu être actualisée. Actualisez la page pour consulter son statut.`)
      }
    } catch (erreur) {
      setMessage(erreur.response?.data?.erreur || (erreur.response?.status === 403
        ? 'Approbation refusée : seul un administrateur ou un validateur peut approuver les paiements.'
        : 'Approbation impossible : vérifiez votre connexion et réessayez.'))
    } finally {
      setApprobationEnCours(null)
    }
  }
  const supprimer = async (id) => { await api.delete(`/transactions/${id}`); await load() }

  const controler = async () => {
    setMessage('')
    const { data } = await api.post('/transactions/controler', null, { libelleChargement: 'Contrôles métier…' })
    setMessage(data.valide
      ? 'Contrôles métier : OK'
      : `Erreurs : ${data.erreurs.join(', ')}`)
  }

  // Détection IA des montants suspects (Isolation Forest).
  const analyserMontantsIA = async () => {
    setMessage('')
    const { data } = await api.post('/ai/detect', null, { libelleChargement: 'Analyse IA en cours…' })
    if (!data.disponible) {
      setIaResult({ disponible: false })
      return
    }
    const { data: txs } = await api.get('/transactions')
    setTransactions(txs)
    const byId = Object.fromEntries(txs.map((t) => [t.id, t]))
    const suspects = data.resultats
      .filter((r) => r.isAnomaly)
      .map((r) => {
        const t = byId[r.ficheId] || {}
        return {
          id: r.ficheId,
          nom: t.nomBeneficiaire || '',
          employe: t.employeId || '',
          montant: t.montant,
          devise: t.devise || 'EUR',
          score: r.score,
          raisons: r.raisons || [],
          recommandation: t.recommandation || '',
        }
      })
      .sort((a, b) => b.score - a.score)
    setIaResult({ disponible: true, modelVersion: data.modelVersion, suspects })
  }

  return (
    <div>
      <h2>Transactions</h2>

      {peutPreparer(user?.role) && (
        <form className="inline-form" onSubmit={handleCreate}>
          <select name="clientId" value={form.clientId} onChange={handleChange} required>
            <option value="">{societes.length ? 'Société' : 'Aucune société affectée'}</option>
            {societes.map((s) => <option key={s.code} value={s.code}>{s.code} — {s.nom}</option>)}
          </select>
          <input name="employeId" placeholder="ID employé" value={form.employeId} onChange={handleChange} required />
          <input name="nomBeneficiaire" placeholder="Bénéficiaire" value={form.nomBeneficiaire} onChange={handleChange} required />
          <input name="iban" placeholder="IBAN" value={form.iban} onChange={handleChange} required />
          <input name="bic" placeholder="BIC" value={form.bic} onChange={handleChange} />
          <input name="montant" type="number" step="0.01" placeholder="Montant" value={form.montant} onChange={handleChange} required />
          <input name="libelle" placeholder="Libellé" value={form.libelle} onChange={handleChange} />
          <button type="submit">Ajouter</button>
        </form>
      )}

      <div className="actions">
        <ActionButton onClick={controler}>Contrôles métier</ActionButton>
        <ActionButton onClick={analyserMontantsIA}>Analyser (IA)</ActionButton>
      </div>
      {message && <div className="info">{message}</div>}

      {iaResult && !iaResult.disponible && (
        <div className="ia-alert ia-alert--off">Détection IA indisponible : le traitement continue avec les seuls contrôles métier.</div>
      )}
      {iaResult && iaResult.disponible && iaResult.suspects.length === 0 && (
        <div className="ia-alert ia-alert--ok">Aucun montant suspect détecté (modèle {iaResult.modelVersion}).</div>
      )}
      {iaResult && iaResult.disponible && iaResult.suspects.length > 0 && (
        <div className="ia-alert ia-alert--warn">
          <strong>{iaResult.suspects.length} montant(s) suspect(s) détecté(s)</strong>
          <span className="ia-alert__model"> — modèle {iaResult.modelVersion}</span>
          <ul>
            {iaResult.suspects.map((s) => (
              <li key={s.id}>
                Fiche #{s.id} — <strong>{s.employe} / {s.nom}</strong> :{' '}
                <strong>{Number(s.montant).toLocaleString('fr-FR', { minimumFractionDigits: 2 })} {s.devise}</strong>
                {' '}(score {Number(s.score).toFixed(2).replace('.', ',')}) — {s.raisons.join(' ; ')}
                {s.recommandation && (
                  <div className="ia-reco">Recommandation : {s.recommandation}</div>
                )}
              </li>
            ))}
          </ul>
          <p className="ia-alert__hint">
            Le suivi complet (statut, « Marquer corrigée », « Ignorer ») est disponible dans le menu <strong>Anomalies</strong>.
          </p>
        </div>
      )}

      <table>
        <thead>
          <tr>
            <th>ID</th><th>Société</th><th>Employé</th><th>Bénéficiaire</th><th>IBAN</th>
            <th>Montant</th><th>Statut</th><th>Risque</th><th>Score</th><th>Source</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((t) => (
            <tr key={t.id} className={t.sourceAnomalie === 'IA' ? 'row-suspect' : undefined}>
              <td>{t.id}</td>
              <td style={{ whiteSpace: 'nowrap' }}>{t.clientId || '—'}</td>
              <td>{t.employeId}</td>
              <td>{t.nomBeneficiaire}</td>
              <td style={{ whiteSpace: 'nowrap' }}>{masquerIban(t.iban)}</td>
              <td>{afficherMontant(t)}</td>
              <td>
                {(() => {
                  const s = STATUTS[t.statut] || { label: t.statut, color: 'default' }
                  return <Tag color={s.color}>{s.label}</Tag>
                })()}
              </td>
              <td>{t.niveauRisque || '—'}</td>
              <td>{t.scoreRisque != null ? Number(t.scoreRisque).toFixed(2).replace('.', ',') : '—'}</td>
              <td>
                {t.sourceAnomalie === 'IA' ? (
                  <span className="badge-suspect" title={t.raisonsAnomalie || ''}>Montant suspect (IA)</span>
                ) : (t.sourceAnomalie || '—')}
              </td>
              <td>
                {peutValider(user?.role) && (
                  <button onClick={() => approuver(t.id)} disabled={approbationEnCours !== null || t.statut === 'APPROUVEE'}>Approuver</button>
                )}
                {peutPreparer(user?.role) && (
                  <ActionButton
                    onClick={() => supprimer(t.id)}
                    succes={`Transaction n° ${t.id} supprimée.`}
                    confirmation={{ titre: `Supprimer la transaction n° ${t.id} ?`, danger: true, libelle: 'Supprimer' }}
                  >
                    Supprimer
                  </ActionButton>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
