import { useEffect, useState } from 'react'
import api from '../api/client'

const empty = { employeId: '', nomBeneficiaire: '', iban: '', bic: '', montant: '', libelle: '' }

export default function Transactions() {
  const [transactions, setTransactions] = useState([])
  const [form, setForm] = useState(empty)
  const [message, setMessage] = useState('')
  const [iaResult, setIaResult] = useState(null)

  const load = () => api.get('/transactions').then(({ data }) => setTransactions(data))

  useEffect(() => { load() }, [])

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const handleCreate = async (e) => {
    e.preventDefault()
    await api.post('/transactions', { ...form, montant: parseFloat(form.montant) })
    setForm(empty)
    load()
  }

  const approuver = async (id) => { await api.put(`/transactions/${id}/approuver`); load() }
  const supprimer = async (id) => { await api.delete(`/transactions/${id}`); load() }

  const controler = async () => {
    const { data } = await api.post('/transactions/controler')
    setMessage(data.valide
      ? 'Contrôles métier : OK'
      : `Erreurs : ${data.erreurs.join(', ')}`)
  }

  const analyser = async () => {
    await api.post('/transactions/analyser')
    setMessage('Analyse IA terminée')
    load()
  }

  // Détection IA des montants suspects (module additif).
  const analyserMontantsIA = async () => {
    setMessage('')
    const { data } = await api.post('/ai/detect')
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

      <form className="inline-form" onSubmit={handleCreate}>
        <input name="employeId" placeholder="ID employé" value={form.employeId} onChange={handleChange} required />
        <input name="nomBeneficiaire" placeholder="Bénéficiaire" value={form.nomBeneficiaire} onChange={handleChange} required />
        <input name="iban" placeholder="IBAN" value={form.iban} onChange={handleChange} required />
        <input name="bic" placeholder="BIC" value={form.bic} onChange={handleChange} />
        <input name="montant" type="number" step="0.01" placeholder="Montant" value={form.montant} onChange={handleChange} required />
        <input name="libelle" placeholder="Libellé" value={form.libelle} onChange={handleChange} />
        <button type="submit">Ajouter</button>
      </form>

      <div className="actions">
        <button onClick={controler}>Contrôles métier</button>
        <button onClick={analyser}>Analyser (IA)</button>
        <button onClick={analyserMontantsIA}>Analyser montants (IA)</button>
      </div>
      {message && <div className="info">{message}</div>}

      {iaResult && !iaResult.disponible && (
        <div className="ia-alert ia-alert--off">⚠️ Détection IA indisponible — le traitement continue sans anomalies IA.</div>
      )}
      {iaResult && iaResult.disponible && iaResult.suspects.length === 0 && (
        <div className="ia-alert ia-alert--ok">✅ Aucun montant suspect détecté (modèle {iaResult.modelVersion}).</div>
      )}
      {iaResult && iaResult.disponible && iaResult.suspects.length > 0 && (
        <div className="ia-alert ia-alert--warn">
          <strong>⚠️ {iaResult.suspects.length} montant(s) suspect(s) détecté(s)</strong>
          <span className="ia-alert__model"> — modèle {iaResult.modelVersion}</span>
          <ul>
            {iaResult.suspects.map((s) => (
              <li key={s.id}>
                Fiche #{s.id} — <strong>{s.employe} / {s.nom}</strong> :{' '}
                <strong>{Number(s.montant).toLocaleString('fr-FR', { minimumFractionDigits: 2 })} {s.devise}</strong>
                {' '}(score {s.score}) — {s.raisons.join(' ; ')}
                {s.recommandation && (
                  <div className="ia-reco">💡 Recommandation : {s.recommandation}</div>
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
            <th>ID</th><th>Employé</th><th>Bénéficiaire</th><th>IBAN</th>
            <th>Montant</th><th>Statut</th><th>Risque</th><th>Score</th><th>Source</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((t) => (
            <tr key={t.id} className={t.sourceAnomalie === 'IA' ? 'row-suspect' : undefined}>
              <td>{t.id}</td>
              <td>{t.employeId}</td>
              <td>{t.nomBeneficiaire}</td>
              <td>{t.iban}</td>
              <td>{t.montant} {t.devise}</td>
              <td>{t.statut}</td>
              <td>{t.niveauRisque || '—'}</td>
              <td>{t.scoreRisque != null ? t.scoreRisque : '—'}</td>
              <td>
                {t.sourceAnomalie === 'IA' ? (
                  <span className="badge-suspect" title={t.raisonsAnomalie || ''}>⚠️ Montant suspect (IA)</span>
                ) : (t.sourceAnomalie || '—')}
              </td>
              <td>
                <button onClick={() => approuver(t.id)}>Approuver</button>
                <button onClick={() => supprimer(t.id)}>Supprimer</button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
