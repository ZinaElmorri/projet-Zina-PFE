import { useEffect, useState } from 'react'
import api from '../api/client'

export default function Dashboard() {
  const [stats, setStats] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/dashboard/stats')
      .then(({ data }) => setStats(data))
      .catch(() => setError('Impossible de charger les statistiques'))
  }, [])

  if (error) return <p className="error">{error}</p>
  if (!stats) return <p>Chargement…</p>

  const cards = [
    { label: 'Transactions', value: stats.totalTransactions },
    { label: 'Approuvées', value: stats.transactionsApprouvees },
    { label: 'Anomalies détectées', value: stats.anomaliesDetectees },
    { label: 'Fichiers générés', value: stats.fichiersGeneres },
    { label: 'Erreurs XSD', value: stats.erreursXsd },
    { label: 'Schémas XSD', value: stats.schemasXsd },
  ]

  return (
    <div>
      <h2>Tableau de bord</h2>
      <div className="cards">
        {cards.map((c) => (
          <div className="card" key={c.label}>
            <span className="card-value">{c.value}</span>
            <span className="card-label">{c.label}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
