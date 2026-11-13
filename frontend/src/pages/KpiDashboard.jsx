import { useEffect, useState } from 'react'
import { Alert, Button, Card, Col, DatePicker, Empty, Input, Progress, Row, Skeleton, Space, Table, Tag, Tooltip, Typography } from 'antd'
import {
  AlertOutlined,
  BankOutlined,
  EuroOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  PercentageOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import api from '../api/client'
import PageHeader from '../components/PageHeader.jsx'

const nf = new Intl.NumberFormat('fr-FR')
const euro = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' })

const COULEURS = { primaire: '#1F3A5F', bleu: '#2D5283', vert: '#2E7D32', orange: '#D97706', rouge: '#C62828', or: '#B8893B', gris: '#8C99AB' }
const SOURCES = { REGLE: COULEURS.bleu, XSD: COULEURS.orange, IA: COULEURS.vert }

// Les données du jour sont dans le mois en cours : c'est la période affichée à l'ouverture.
const moisEnCours = () => dayjs().format('YYYY-MM')

const couleurTaux = (t) => (t < 1 ? COULEURS.vert : t <= 5 ? COULEURS.orange : COULEURS.rouge)
const fmtPct = (t) => `${nf.format(Number(t))} %`

/** Carte indicateur : icône, valeur, libellé, détail facultatif. */
function Indicateur({ icone, couleur, valeur, libelle, detail }) {
  return (
    <Card className="kpi-carte" styles={{ body: { padding: 18 } }} style={{ borderTop: `3px solid ${couleur}`, height: '100%' }}>
      <div className="kpi-carte__tete">
        <span className="kpi-carte__icone" style={{ background: `${couleur}1A`, color: couleur }}>{icone}</span>
        <Typography.Text type="secondary">{libelle}</Typography.Text>
      </div>
      <div className="kpi-carte__valeur" style={{ color: couleur }}>{valeur}</div>
      {detail && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{detail}</Typography.Text>}
    </Card>
  )
}

const Vide = ({ texte = 'Aucune donnée pour cette période' }) => (
  <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={texte} style={{ margin: '16px 0' }} />
)

// Barres horizontales avec valeur.
function Barres({ data, couleur }) {
  if (data.length === 0) return <Vide />
  const max = Math.max(1, ...data.map((d) => d.value))
  return (
    <div className="kpi-barres">
      {data.map((d) => (
        <div className="kpi-barres__ligne" key={d.label}>
          <Tooltip title={d.label}><span className="kpi-barres__label">{d.label.replaceAll('_', ' ')}</span></Tooltip>
          <span className="kpi-barres__valeur">{nf.format(d.value)}</span>
          <Progress percent={(d.value / max) * 100} showInfo={false} strokeColor={couleur} size={['100%', 10]} />
        </div>
      ))}
    </div>
  )
}

// Anneau SVG avec le total au centre.
function Anneau({ data }) {
  const total = data.reduce((s, d) => s + d.value, 0)
  if (total === 0) return <Vide />
  const r = 54, c = 2 * Math.PI * r
  let decalage = 0
  return (
    <div className="kpi-anneau">
      <svg width="170" height="170" viewBox="0 0 170 170" role="img" aria-label="Anomalies par source">
        <g transform="translate(85,85) rotate(-90)">
          {data.map((d) => {
            const part = d.value / total
            const segment = (
              <circle key={d.label} r={r} fill="none" strokeWidth="22" stroke={SOURCES[d.label] || COULEURS.gris}
                strokeDasharray={`${part * c} ${c}`} strokeDashoffset={-decalage * c}>
                <title>{`${d.label} : ${nf.format(d.value)}`}</title>
              </circle>
            )
            decalage += part
            return segment
          })}
        </g>
        <text x="85" y="82" textAnchor="middle" fontSize="26" fontWeight="700" fill={COULEURS.primaire}>{nf.format(total)}</text>
        <text x="85" y="102" textAnchor="middle" fontSize="11" fill="#667085">anomalies</text>
      </svg>
      <ul className="kpi-legende">
        {data.map((d) => (
          <li key={d.label}>
            <span className="kpi-legende__point" style={{ background: SOURCES[d.label] || COULEURS.gris }} />
            <span>{d.label}</span>
            <strong>{nf.format(d.value)}</strong>
            <span className="kpi-legende__part">{Math.round((d.value / total) * 100)} %</span>
          </li>
        ))}
      </ul>
    </div>
  )
}

// Évolution sur 12 mois : fiches (barres) et taux d'erreur (courbe).
function Evolution({ points }) {
  if (!points || points.length === 0 || points.every((p) => p.fichesGenerees === 0 && Number(p.tauxErreur) === 0)) {
    return <Vide texte="Aucune fiche générée sur les 12 derniers mois" />
  }
  const w = 640, h = 220, gauche = 34, bas = 26, haut = 12, droite = 12
  const maxF = Math.max(1, ...points.map((p) => p.fichesGenerees))
  const maxT = Math.max(1, ...points.map((p) => Number(p.tauxErreur)))
  const pas = (w - gauche - droite) / points.length
  const x = (i) => gauche + pas * i + pas / 2
  const yF = (v) => h - bas - (v / maxF) * (h - bas - haut)
  const yT = (v) => h - bas - (v / maxT) * (h - bas - haut)
  const largeur = Math.min(26, pas * 0.5)
  const courbe = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${yT(Number(p.tauxErreur))}`).join(' ')
  return (
    <>
      <svg width="100%" viewBox={`0 0 ${w} ${h}`} role="img" aria-label="Évolution sur 12 mois">
        {[0, 0.5, 1].map((f) => (
          <line key={f} x1={gauche} x2={w - droite} y1={h - bas - f * (h - bas - haut)} y2={h - bas - f * (h - bas - haut)} stroke="#E5E9F0" />
        ))}
        {[0, 0.5, 1].map((f) => (
          <text key={`g${f}`} x={gauche - 6} y={h - bas - f * (h - bas - haut) + 4} textAnchor="end" fontSize="10" fill="#8C99AB">{Math.round(maxF * f)}</text>
        ))}
        {points.map((p, i) => (
          <rect key={p.periode} x={x(i) - largeur / 2} y={yF(p.fichesGenerees)} width={largeur} rx="3"
            height={Math.max(0, h - bas - yF(p.fichesGenerees))} fill={COULEURS.bleu} opacity="0.85">
            <title>{`${p.periode} : ${nf.format(p.fichesGenerees)} fiche(s), taux d'erreur ${fmtPct(p.tauxErreur)}`}</title>
          </rect>
        ))}
        <path d={courbe} fill="none" stroke={COULEURS.rouge} strokeWidth="2" strokeLinejoin="round" />
        {points.map((p, i) => <circle key={`c${p.periode}`} cx={x(i)} cy={yT(Number(p.tauxErreur))} r="3.5" fill="#fff" stroke={COULEURS.rouge} strokeWidth="2" />)}
        {points.map((p, i) => (
          <text key={`t${p.periode}`} x={x(i)} y={h - 8} fontSize="10" textAnchor="middle" fill="#667085">
            {dayjs(p.periode + '-01').format('MMM').replace('.', '')}
          </text>
        ))}
      </svg>
      <Space size="large" className="kpi-legende-ligne">
        <span><span className="kpi-legende__point" style={{ background: COULEURS.bleu }} />Fiches générées</span>
        <span><span className="kpi-legende__point" style={{ background: COULEURS.rouge }} />Taux d'erreur</span>
      </Space>
    </>
  )
}

const Panneau = ({ titre, children }) => (
  <Card title={titre} style={{ height: '100%' }} styles={{ header: { minHeight: 48 } }}>{children}</Card>
)

export default function KpiDashboard() {
  const [periode, setPeriode] = useState(moisEnCours())
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

  const lots = kpi && kpi.lotsValidesXsd != null ? `${kpi.lotsValidesXsd} / ${kpi.lotsGeneres}` : '—'
  const statuts = kpi?.anomaliesParStatut

  return (
    <div>
      <PageHeader
        title="Indicateurs des flux SEPA"
        subtitle={kpi ? `Période : ${dayjs(kpi.periode + '-01').format('MMMM YYYY')}` : 'Chargement des indicateurs…'}
      />

      <Card style={{ margin: '16px 0' }} styles={{ body: { padding: 16 } }}>
        <Space wrap size="middle" align="end">
          <label>
            <div className="kpi-champ">Période</div>
            <DatePicker
              picker="month"
              allowClear={false}
              format="MMMM YYYY"
              value={dayjs(periode + '-01')}
              onChange={(d) => d && setPeriode(d.format('YYYY-MM'))}
              style={{ width: 190 }}
            />
          </label>
          <label>
            <div className="kpi-champ">Client</div>
            <Input placeholder="Tous les clients" allowClear value={clientId} onChange={(e) => setClientId(e.target.value)} onPressEnter={load} style={{ width: 220 }} />
          </label>
          <Button type="primary" icon={<ReloadOutlined />} onClick={load} loading={loading}>Appliquer</Button>
        </Space>
      </Card>

      {error && <Alert type="error" showIcon message={error} style={{ marginBottom: 16 }} />}
      {loading && !kpi && <Skeleton active paragraph={{ rows: 8 }} />}

      {kpi && (
        <div className={loading ? 'kpi-recharge' : undefined}>
          <Row gutter={[16, 16]}>
            <Col xs={24} sm={12} lg={8} xxl={4}>
              <Indicateur icone={<FileTextOutlined />} couleur={COULEURS.bleu} valeur={nf.format(kpi.fichesGenerees)} libelle="Fiches de paie générées" />
            </Col>
            <Col xs={24} sm={12} lg={8} xxl={4}>
              <Indicateur icone={<BankOutlined />} couleur={COULEURS.primaire} valeur={nf.format(kpi.clientsConcernes)} libelle="Clients concernés" />
            </Col>
            <Col xs={24} sm={12} lg={8} xxl={4}>
              <Indicateur icone={<PercentageOutlined />} couleur={couleurTaux(Number(kpi.tauxErreur))} valeur={fmtPct(kpi.tauxErreur)}
                libelle="Taux d'erreur" detail={`${nf.format(kpi.nbErreurs)} erreur(s) sur ${nf.format(kpi.fichesGenerees)} fiche(s)`} />
            </Col>
            <Col xs={24} sm={12} lg={8} xxl={4}>
              <Indicateur icone={<EuroOutlined />} couleur={COULEURS.or} valeur={euro.format(kpi.montantTotalVire || 0)} libelle="Montant total viré" />
            </Col>
            <Col xs={24} sm={12} lg={8} xxl={4}>
              <Indicateur icone={<FileDoneOutlined />} couleur={COULEURS.vert} valeur={lots} libelle="Lots valides XSD"
                detail={kpi.lotsValidesXsd == null ? 'Donnée non disponible' : undefined} />
            </Col>
            <Col xs={24} sm={12} lg={8} xxl={4}>
              <Indicateur icone={<AlertOutlined />} couleur={COULEURS.orange} libelle="Anomalies"
                valeur={<span>{nf.format(statuts.ouvertes)}<span className="kpi-sep"> / </span>{nf.format(statuts.corrigees)}<span className="kpi-sep"> / </span>{nf.format(statuts.ignorees)}</span>}
                detail="ouvertes / corrigées / ignorées" />
            </Col>
          </Row>

          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={24} xl={12}>
              <Panneau titre="Évolution sur 12 mois"><Evolution points={kpi.evolutionMensuelle} /></Panneau>
            </Col>
            <Col xs={24} md={12} xl={6}>
              <Panneau titre="Anomalies par source"><Anneau data={kpi.anomaliesParSource} /></Panneau>
            </Col>
            <Col xs={24} md={12} xl={6}>
              <Panneau titre="Anomalies par type"><Barres data={kpi.anomaliesParType} couleur={COULEURS.bleu} /></Panneau>
            </Col>
            <Col xs={24} md={12}>
              <Panneau titre="Clients par département"><Barres data={kpi.clientsParDepartement} couleur={COULEURS.vert} /></Panneau>
            </Col>
            <Col xs={24} md={12}>
              <Panneau titre="Top 5 clients à anomalies">
                {kpi.topClientsAnomalies.length === 0 ? <Vide texte="Aucune anomalie par client" /> : (
                  <Table
                    size="small"
                    pagination={false}
                    rowKey="clientId"
                    dataSource={kpi.topClientsAnomalies}
                    columns={[
                      { title: 'Client', dataIndex: 'clientId' },
                      { title: 'Anomalies', dataIndex: 'nbAnomalies', align: 'right', render: (n) => <Tag color="orange">{nf.format(n)}</Tag> },
                    ]}
                  />
                )}
              </Panneau>
            </Col>
          </Row>
        </div>
      )}
    </div>
  )
}
