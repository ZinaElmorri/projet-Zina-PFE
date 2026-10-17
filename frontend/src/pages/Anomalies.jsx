import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Pagination, Spin } from 'antd'
import api from '../api/client'

const TAILLE_PAGE = 50

const STATUTS = [
  { key: '', label: 'Toutes' },
  { key: 'OUVERTE', label: 'Ouvertes' },
  { key: 'CORRIGEE', label: 'Corrigées' },
  { key: 'IGNOREE', label: 'Ignorées' },
]

const pastille = (statut) => {
  const map = { OUVERTE: 'badge-ouverte', CORRIGEE: 'badge-corrigee', IGNOREE: 'badge-ignoree' }
  const label = { OUVERTE: 'Ouverte', CORRIGEE: 'Corrigée', IGNOREE: 'Ignorée' }
  return <span className={`badge-statut ${map[statut] || ''}`}>{label[statut] || statut}</span>
}

export default function Anomalies() {
  const [anomalies, setAnomalies] = useState([])
  const [filtre, setFiltre] = useState('')
  const [expanded, setExpanded] = useState({})
  const [message, setMessage] = useState('')
  const [loading, setLoading] = useState(true)
  const [page, setPage] = useState(1)

  const load = (statut = filtre) => {
    setLoading(true)
    return api.get('/anomalies', { params: statut ? { statut } : {} })
      .then(({ data }) => setAnomalies(data))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const changerFiltre = (statut) => { setFiltre(statut); setPage(1); load(statut) }

  const relancer = async () => {
    const { data } = await api.post('/anomalies/detecter')
    setMessage(`Détection relancée : ${data.nouvelles} nouvelle(s) — ${data.ouvertes} ouverte(s) / ${data.corrigees} corrigée(s) / ${data.ignorees} ignorée(s)`)
    load()
  }

  const marquerCorrigee = async (id) => {
    await api.patch(`/anomalies/${id}/statut`, { statut: 'CORRIGEE', commentaire: '' })
    load()
  }

  const ignorer = async (id) => {
    const commentaire = window.prompt('Motif de l\u2019ignorance (obligatoire) :')
    if (commentaire == null) return
    if (!commentaire.trim()) { setMessage('Un commentaire est obligatoire pour ignorer une anomalie.'); return }
    await api.patch(`/anomalies/${id}/statut`, { statut: 'IGNOREE', commentaire })
    load()
  }

  const compter = (s) => anomalies.filter((a) => a.statut === s).length

  // Seule la page courante est rendue : afficher des milliers de lignes d'un coup bloque le navigateur.
  const pageMax = Math.max(1, Math.ceil(anomalies.length / TAILLE_PAGE))
  const pageCourante = Math.min(page, pageMax)
  const visibles = anomalies.slice((pageCourante - 1) * TAILLE_PAGE, pageCourante * TAILLE_PAGE)
  const premierChargement = loading && anomalies.length === 0

  return (
    <div>
      <h2>Anomalies</h2>

      <div className="actions">
        {STATUTS.map((s) => (
          <button key={s.key} className={filtre === s.key ? '' : 'secondary'} onClick={() => changerFiltre(s.key)}>
            {s.label}
          </button>
        ))}
        <button onClick={relancer}>Relancer la détection</button>
      </div>

      <div className="info">
        {premierChargement
          ? <><Spin size="small" /> Chargement des anomalies…</>
          : <>{compter('OUVERTE')} ouvertes / {compter('CORRIGEE')} corrigées / {compter('IGNOREE')} ignorées</>}
        {loading && !premierChargement && <Spin size="small" style={{ marginLeft: 12 }} />}
      </div>
      {message && <div className="info">{message}</div>}

      <table>
        <thead>
          <tr>
            <th>Type</th><th>Salarié</th><th>IBAN</th><th>Montant</th>
            <th>Recommandation</th><th>Statut</th><th>Actions</th>
          </tr>
        </thead>
        <tbody>
          {visibles.map((a) => (
            <tr key={a.id} className={a.statut === 'OUVERTE' ? 'row-suspect' : undefined}>
              <td>{a.type}</td>
              <td>{a.matricule}</td>
              <td>{a.ibanMasque}</td>
              <td>{a.montant != null ? `${a.montant}` : '—'}</td>
              <td>
                {expanded[a.id] || (a.recommandation || '').length <= 90
                  ? a.recommandation
                  : `${a.recommandation.slice(0, 90)}… `}
                {(a.recommandation || '').length > 90 && (
                  <button className="link-btn" onClick={() => setExpanded({ ...expanded, [a.id]: !expanded[a.id] })}>
                    {expanded[a.id] ? 'Voir moins' : 'Voir plus'}
                  </button>
                )}
              </td>
              <td>{pastille(a.statut)}</td>
              <td>
                <Link to="/transactions"><button className="secondary">Ouvrir la fiche</button></Link>
                {a.statut !== 'CORRIGEE' && <button onClick={() => marquerCorrigee(a.id)}>Marquer corrigée</button>}
                {a.statut !== 'IGNOREE' && <button className="secondary" onClick={() => ignorer(a.id)}>Ignorer</button>}
              </td>
            </tr>
          ))}
          {premierChargement && (
            <tr><td colSpan="7" style={{ textAlign: 'center', padding: 32 }}><Spin /></td></tr>
          )}
          {!premierChargement && anomalies.length === 0 && (
            <tr><td colSpan="7">Aucune anomalie.</td></tr>
          )}
        </tbody>
      </table>
      {anomalies.length > TAILLE_PAGE && (
        <Pagination
          style={{ marginTop: 16, justifyContent: 'flex-end' }}
          current={pageCourante}
          pageSize={TAILLE_PAGE}
          total={anomalies.length}
          showSizeChanger={false}
          showTotal={(total, [debut, fin]) => `${debut}-${fin} sur ${total}`}
          onChange={(p) => { setPage(p); window.scrollTo({ top: 0 }) }}
        />
      )}
    </div>
  )
}
