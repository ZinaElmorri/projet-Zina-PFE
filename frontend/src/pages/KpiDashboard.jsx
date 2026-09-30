import { useEffect, useState } from 'react'
import api from '../api/client'

const nf = new Intl.NumberFormat('fr-FR')
const euro = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' })

const moisDernier = () => {
  const d = new Date()
  d.setMonth(d.getMonth() - 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}`
}

const couleurTaux = (t) => (t < 1 ? '#1e9e54' : t <= 5 ? '#ff8c00' : '#c0392b')
const fmtPct = (t) => `${nf.format(Number(t))} %`.replace('.', ',')

// Barres horizontales simples.
function Bars({ data, couleur = '#464feb' }) {
  const max = Math.max(1, ...data.map((d) => d.value))
  if (data.length === 0) return <p className="muted">Donnée indisponible</p>
  return (
    <div className="chart-bars">
      {data.map((d) => (
        <div className="bar-row" key={d.label}>
          <span className="bar-label" title={d.label}>{d.label}</span>
          <span className="bar-track"><span className="bar-fill" style={{ width: `${(d.value / max) * 100}%`, background: couleur }} /></span>
          <span className="bar-value">{nf.format(d.value)}</span>
        </div>
      ))}
    </div>
  )
}

// Anneau (doughnut) en SVG.
function Doughnut({ data }) {
  const total = data.reduce((s, d) => s + d.value, 0)
  if (total === 0) return <p className="muted">Donnée indisponible</p>
  const couleurs = { REGLE: '#464feb', XSD: '#ff8c00', IA: '#1e9e54' }
  const r = 60, c = 2 * Math.PI * r
  let offset = 0
  return (
    <div className="doughnut">
      <svg width="160" height="160" viewBox="0 0 160 160">
        <g transform="translate(80,80) rotate(-90)">
          {data.map((d) => {
            const frac = d.value / total
            const dash = `${frac * c} ${c}`
            const seg = (
              <circle key={d.label} r={r} fill="none" strokeWidth="24"
                stroke={couleurs[d.label] || '#888'} strokeDasharray={dash} strokeDashoffset={-offset * c} />
            )
            offset += frac
            return seg
          })}
        </g>
      </svg>
      <ul className="legend">
        {data.map((d) => (
          <li key={d.label}><span className="dot" style={{ background: couleurs[d.label] || '#888' }} />{d.label} : {nf.format(d.value)}</li>
        ))}
      </ul>
    </div>
  )
}

// Courbe 12 mois : fiches (barres) + taux d'erreur (ligne).
function LineChart({ points }) {
  if (!points || points.length === 0) return <p className="muted">Donnée indisponible</p>
  const w = 620, h = 180, pad = 28
  const maxF = Math.max(1, ...points.map((p) => p.fichesGenerees))
  const maxT = Math.max(1, ...points.map((p) => Number(p.tauxErreur)))
  const stepX = (w - pad * 2) / Math.max(1, points.length - 1)
  const x = (i) => pad + i * stepX
  const yF = (v) => h - pad - (v / maxF) * (h - pad * 2)
  const yT = (v) => h - pad - (v / maxT) * (h - pad * 2)
  const ligne = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${yT(Number(p.tauxErreur))}`).join(' ')
  const bw = Math.max(4, stepX * 0.4)
  return (
    <svg width="100%" viewBox={`0 0 ${w} ${h}`} className="linechart">
      {points.map((p, i) => (
        <rect key={p.periode} x={x(i) - bw / 2} y={yF(p.fichesGenerees)} width={bw}
          height={h - pad - yF(p.fichesGenerees)} fill="#c7cbe0" />
      ))}
      <path d={ligne} fill="none" stroke="#c0392b" strokeWidth="2" />
      {points.map((p, i) => <circle key={`c${p.periode}`} cx={x(i)} cy={yT(Number(p.tauxErreur))} r="3" fill="#c0392b" />)}
      {points.map((p, i) => (
        <text key={`t${p.periode}`} x={x(i)} y={h - 8} fontSize="9" textAnchor="middle" fill="#666">
          {p.periode.slice(5)}
        </text>
      ))}
    </svg>
  )
}

export default function KpiDashboard() {
  const [periode, setPeriode] = useState(moisDernier())
  const [clientId, setClientId] = useState('')
  const [kpi, setKpi] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const load = () => {
    setLoading(true)
    setError('')
    const params = { periode }
    if (clientId.trim()) params.clientId = clientId.trim()
    api.get('/dashboard/kpi', { params })
      .then(({ data }) => setKpi(data))
      .catch(() => setError('Impossible de charger les indicateurs'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const val = (v) => (v === null || v === undefined ? '—' : v)

  return (
    <div>
      <h2>Indicateurs (KPI)</h2>

      <div className="inline-form">
        <label>Période <input type="month" value={periode} onChange={(e) => setPeriode(e.target.value)} /></label>
        <label>Client <input placeholder="Tous" value={clientId} onChange={(e) => setClientId(e.target.value)} /></label>
        <button onClick={load}>Appliquer</button>
      </div>

      {error && <div className="error">{error}</div>}
      {loading && <div className="info">Chargement… <span className="spinner" /></div>}

      {kpi && !loading && (
        <>
          <div className="cards">
            <div className="card"><span className="card-value">{val(nf.format(kpi.fichesGenerees))}</span><span className="card-label">Fiches de paie générées</span></div>
            <div className="card"><span className="card-value">{val(nf.format(kpi.clientsConcernes))}</span><span className="card-label">Clients concernés</span></div>
            <div className="card">
              <span className="card-value" style={{ color: couleurTaux(Number(kpi.tauxErreur)) }}>{fmtPct(kpi.tauxErreur)}</span>
              <span className="card-label">Taux d'erreur ({nf.format(kpi.nbErreurs)} / {nf.format(kpi.fichesGenerees)})</span>
            </div>
            <div className="card"><span className="card-value">{val(euro.format(kpi.montantTotalVire || 0))}</span><span className="card-label">Montant total viré</span></div>
            <div className="card"><span className="card-value">{kpi.lotsValidesXsd == null ? '—' : `${kpi.lotsValidesXsd} / ${kpi.lotsGeneres}`}</span><span className="card-label">Lots valides XSD</span></div>
            <div className="card">
              <span className="card-value">{nf.format(kpi.anomaliesParStatut.ouvertes)} / {nf.format(kpi.anomaliesParStatut.corrigees)} / {nf.format(kpi.anomaliesParStatut.ignorees)}</span>
              <span className="card-label">Anomalies ouvertes / corrigées / ignorées</span>
            </div>
          </div>

          <div className="kpi-grid">
            <section className="kpi-panel">
              <h3>Évolution 12 mois (fiches + taux d'erreur)</h3>
              <LineChart points={kpi.evolutionMensuelle} />
            </section>
            <section className="kpi-panel">
              <h3>Anomalies par source</h3>
              <Doughnut data={kpi.anomaliesParSource} />
            </section>
            <section className="kpi-panel">
              <h3>Anomalies par type</h3>
              <Bars data={kpi.anomaliesParType} />
            </section>
            <section className="kpi-panel">
              <h3>Clients par département</h3>
              <Bars data={kpi.clientsParDepartement} couleur="#1e9e54" />
            </section>
            <section className="kpi-panel">
              <h3>Top 5 clients à anomalies</h3>
              {kpi.topClientsAnomalies.length === 0 ? <p className="muted">Aucune donnée</p> : (
                <table>
                  <thead><tr><th>Client</th><th>Anomalies</th></tr></thead>
                  <tbody>
                    {kpi.topClientsAnomalies.map((c) => (
                      <tr key={c.clientId}><td>{c.clientId}</td><td>{nf.format(c.nbAnomalies)}</td></tr>
                    ))}
                  </tbody>
                </table>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  )
}
