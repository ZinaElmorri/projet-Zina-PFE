import { useEffect, useState } from 'react'
import { Alert, Button, Card, Col, DatePicker, Empty, Input, Progress, Row, Skeleton, Space, Tag, Tooltip, Typography } from 'antd'
import {
  AlertOutlined,
  ArrowDownOutlined,
  ArrowUpOutlined,
  BankOutlined,
  CheckCircleOutlined,
  EuroOutlined,
  FileDoneOutlined,
  FileTextOutlined,
  MinusOutlined,
  PercentageOutlined,
  ReloadOutlined,
  RobotOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import api from '../api/client'
import PageHeader from '../components/PageHeader.jsx'
import { peutVoirKpiComplet } from '../constants/roles.js'
import { useAuth } from '../context/AuthContext.jsx'

const nf = new Intl.NumberFormat('fr-FR')
const nf1 = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 })
const euro = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' })

const COULEURS = { primaire: '#1F3A5F', bleu: '#2D5283', vert: '#2E7D32', orange: '#D97706', rouge: '#C62828', or: '#B8893B', gris: '#8C99AB' }
const SOURCES = { REGLE: COULEURS.bleu, XSD: COULEURS.orange, IA: COULEURS.vert }

// Objectifs : `cible` atteint = vert ; entre `cible` et `limite` = orange ; au-delà = rouge.
// sens 'haut' : plus c'est haut, mieux c'est ; 'bas' : plus c'est bas, mieux c'est.
const OBJECTIFS = {
  approbation: { cible: 90, limite: 70, sens: 'haut', libelle: 'Objectif : ≥ 90 %' },
  erreur: { cible: 1, limite: 5, sens: 'bas', libelle: 'Objectif : ≤ 1 %' },
  conformite: { cible: 95, limite: 80, sens: 'haut', libelle: 'Objectif : ≥ 95 %' },
}

const couleurObjectif = (valeur, o) => {
  if (valeur == null) return COULEURS.gris
  if (o.sens === 'haut') return valeur >= o.cible ? COULEURS.vert : valeur >= o.limite ? COULEURS.orange : COULEURS.rouge
  return valeur <= o.cible ? COULEURS.vert : valeur <= o.limite ? COULEURS.orange : COULEURS.rouge
}

// Les données du jour sont dans le mois en cours : c'est la période affichée à l'ouverture.
const moisEnCours = () => dayjs().format('YYYY-MM')
const libelleMois = (periode) => dayjs(`${periode}-01`).format('MMMM YYYY')
const moisPrecedent = (periode) => dayjs(`${periode}-01`).subtract(1, 'month').format('YYYY-MM')

/** Ratio en % ; null si le dénominateur est absent ou nul (affiché « — »). */
const ratio = (numerateur, denominateur) => (denominateur > 0 && numerateur != null ? (numerateur / denominateur) * 100 : null)
const pct = (v) => (v == null ? '—' : `${nf1.format(v)} %`)

// Indicateurs dérivés d'une réponse KPI (mois courant ou précédent).
const indicateurs = (kpi) => (kpi ? {
  approbation: ratio(kpi.fichesGenerees, kpi.transactionsTotal),
  erreur: kpi.fichesGenerees > 0 ? Number(kpi.tauxErreur) : null,
  conformite: ratio(kpi.lotsValidesXsd, kpi.lotsGeneres),
  montant: kpi.fichesGenerees > 0 ? Number(kpi.montantTotalVire) : null,
} : { approbation: null, erreur: null, conformite: null, montant: null })

/** Variation par rapport au mois précédent : flèche verte si c'est dans le bon sens, rouge sinon. */
function Variation({ actuel, precedent, mois, sens, relatif = false }) {
  if (actuel == null || precedent == null) {
    return <Typography.Text type="secondary" style={{ fontSize: 12 }}>vs {mois} : —</Typography.Text>
  }
  const delta = relatif ? (precedent === 0 ? null : ((actuel - precedent) / precedent) * 100) : actuel - precedent
  if (delta == null) return <Typography.Text type="secondary" style={{ fontSize: 12 }}>vs {mois} : —</Typography.Text>
  const nul = Math.abs(delta) < 0.05
  const bon = sens === 'neutre' ? null : sens === 'haut' ? delta > 0 : delta < 0
  const couleur = nul || bon === null ? COULEURS.gris : bon ? COULEURS.vert : COULEURS.rouge
  const Icone = nul ? MinusOutlined : delta > 0 ? ArrowUpOutlined : ArrowDownOutlined
  return (
    <span style={{ fontSize: 12, color: couleur }}>
      <Icone /> {delta > 0 ? '+' : ''}{nf1.format(delta)}{relatif ? ' %' : ' pts'}
      <Typography.Text type="secondary" style={{ fontSize: 12 }}> vs {mois}</Typography.Text>
    </span>
  )
}

/** Carte indicateur : icône, valeur, libellé, objectif et variation facultatifs. */
function Indicateur({ icone, couleur, valeur, libelle, detail, objectif, variation }) {
  return (
    <Card className="kpi-carte" styles={{ body: { padding: 18 } }} style={{ borderTop: `3px solid ${couleur}`, height: '100%' }}>
      <div className="kpi-carte__tete">
        <span className="kpi-carte__icone" style={{ background: `${couleur}1A`, color: couleur }}>{icone}</span>
        <Typography.Text type="secondary">{libelle}</Typography.Text>
      </div>
      <div className="kpi-carte__valeur" style={{ color: couleur }}>{valeur}</div>
      <Space direction="vertical" size={2}>
        {objectif && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{objectif}</Typography.Text>}
        {variation}
        {detail && <Typography.Text type="secondary" style={{ fontSize: 12 }}>{detail}</Typography.Text>}
      </Space>
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

// Évolution sur 12 mois : fiches (barres, axe gauche) et taux d'erreur (courbe, axe droit en %).
function Evolution({ points }) {
  if (!points || points.length === 0 || points.every((p) => p.fichesGenerees === 0 && Number(p.tauxErreur) === 0)) {
    return <Vide texte="Aucune fiche générée sur les 12 derniers mois" />
  }
  const w = 640, h = 240, gauche = 38, droite = 46, bas = 26, haut = 26
  const maxF = Math.max(1, ...points.map((p) => p.fichesGenerees))
  const maxT = Math.max(1, ...points.map((p) => Number(p.tauxErreur)))
  const pas = (w - gauche - droite) / points.length
  const x = (i) => gauche + pas * i + pas / 2
  const yF = (v) => h - bas - (v / maxF) * (h - bas - haut)
  const yT = (v) => h - bas - (v / maxT) * (h - bas - haut)
  const yGrille = (f) => h - bas - f * (h - bas - haut)
  const largeur = Math.min(26, pas * 0.5)
  const courbe = points.map((p, i) => `${i === 0 ? 'M' : 'L'} ${x(i)} ${yT(Number(p.tauxErreur))}`).join(' ')
  return (
    <>
      <svg width="100%" viewBox={`0 0 ${w} ${h}`} role="img" aria-label="Évolution sur 12 mois">
        {[0, 0.5, 1].map((f) => (
          <line key={f} x1={gauche} x2={w - droite} y1={yGrille(f)} y2={yGrille(f)} stroke="#E5E9F0" />
        ))}
        {/* Axe gauche : nombre de fiches */}
        <text x={gauche - 6} y={12} textAnchor="end" fontSize="10" fontWeight="600" fill={COULEURS.bleu}>Fiches</text>
        {[0, 0.5, 1].map((f) => (
          <text key={`g${f}`} x={gauche - 6} y={yGrille(f) + 4} textAnchor="end" fontSize="10" fill={COULEURS.bleu}>{nf1.format(maxF * f)}</text>
        ))}
        {/* Axe droit : taux d'erreur en % */}
        <text x={w - droite + 6} y={12} fontSize="10" fontWeight="600" fill={COULEURS.rouge}>Taux</text>
        {[0, 0.5, 1].map((f) => (
          <text key={`d${f}`} x={w - droite + 6} y={yGrille(f) + 4} fontSize="10" fill={COULEURS.rouge}>{nf1.format(maxT * f)} %</text>
        ))}
        {points.map((p, i) => (
          <rect key={p.periode} x={x(i) - largeur / 2} y={yF(p.fichesGenerees)} width={largeur} rx="3"
            height={Math.max(0, h - bas - yF(p.fichesGenerees))} fill={COULEURS.bleu} opacity="0.85">
            <title>{`${p.periode} : ${nf.format(p.fichesGenerees)} fiche(s), taux d'erreur ${nf1.format(Number(p.tauxErreur))} %`}</title>
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
        <span><span className="kpi-legende__point" style={{ background: COULEURS.bleu }} />Fiches générées (axe gauche)</span>
        <span><span className="kpi-legende__point" style={{ background: COULEURS.rouge }} />Taux d'erreur en % (axe droit)</span>
      </Space>
    </>
  )
}

const Panneau = ({ titre, children }) => (
  <Card title={titre} style={{ height: '100%' }} styles={{ header: { minHeight: 48 } }}>{children}</Card>
)

export default function KpiDashboard() {
  const { user } = useAuth()
  const complet = peutVoirKpiComplet(user?.role)
  const [periode, setPeriode] = useState(moisEnCours())
  const [clientId, setClientId] = useState('')
  const [kpi, setKpi] = useState(null)
  const [precedent, setPrecedent] = useState(null)
  const [kpiIa, setKpiIa] = useState(null)
  const [periodeAffichee, setPeriodeAffichee] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const load = () => {
    setLoading(true)
    setError('')
    const params = (p) => ({ periode: p, ...(clientId.trim() ? { clientId: clientId.trim() } : {}) })
    // Le mois précédent sert à la comparaison : son absence n'empêche pas l'affichage du mois courant.
    Promise.all([
      api.get('/dashboard/kpi', { params: params(periode) }),
      api.get('/dashboard/kpi', { params: params(moisPrecedent(periode)), silent: true }).then((r) => r.data).catch(() => null),
      // Indicateurs IA : leur absence n'empêche pas l'affichage des autres indicateurs.
      api.get('/dashboard/kpi/ia', { params: params(periode), silent: true }).then((r) => r.data).catch(() => null),
    ])
      .then(([courant, prec, ia]) => {
        setKpi(courant.data)
        setPrecedent(prec)
        setKpiIa(ia)
        setPeriodeAffichee(periode)
      })
      .catch(() => setError('Impossible de charger les indicateurs'))
      .finally(() => setLoading(false))
  }

  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const actuel = indicateurs(kpi)
  const avant = indicateurs(precedent)
  const moisAvant = periodeAffichee ? dayjs(`${moisPrecedent(periodeAffichee)}-01`).format('MMM').replace('.', '') : ''
  const statuts = kpi?.anomaliesParStatut
  const lots = kpi && kpi.lotsGeneres != null ? `${kpi.lotsValidesXsd} / ${kpi.lotsGeneres}` : '—'

  return (
    <div>
      <PageHeader
        title="Indicateurs des flux SEPA"
        subtitle={periodeAffichee ? `Période : ${libelleMois(periodeAffichee)} — comparée à ${libelleMois(moisPrecedent(periodeAffichee))}` : 'Chargement des indicateurs…'}
        actions={!complet ? [<Tag key="vue" color="blue">Vue limitée</Tag>] : undefined}
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
            <Col xs={24} sm={12} lg={8} xxl={6}>
              <Indicateur icone={<CheckCircleOutlined />} couleur={couleurObjectif(actuel.approbation, OBJECTIFS.approbation)}
                valeur={pct(actuel.approbation)} libelle="Taux d'approbation" objectif={OBJECTIFS.approbation.libelle}
                detail={actuel.approbation == null ? 'Aucune transaction sur la période' : `${nf.format(kpi.fichesGenerees)} approuvées sur ${nf.format(kpi.transactionsTotal)} transactions`}
                variation={<Variation actuel={actuel.approbation} precedent={avant.approbation} mois={moisAvant} sens="haut" />} />
            </Col>
            <Col xs={24} sm={12} lg={8} xxl={6}>
              <Indicateur icone={<PercentageOutlined />} couleur={couleurObjectif(actuel.erreur, OBJECTIFS.erreur)}
                valeur={pct(actuel.erreur)} libelle="Taux d'erreur des fiches" objectif={OBJECTIFS.erreur.libelle}
                detail={kpi.fichesGenerees > 0 ? `${nf.format(kpi.nbErreurs)} erreur(s) sur ${nf.format(kpi.fichesGenerees)} fiche(s)` : 'Aucune fiche sur la période'}
                variation={<Variation actuel={actuel.erreur} precedent={avant.erreur} mois={moisAvant} sens="bas" />} />
            </Col>
            <Col xs={24} sm={12} lg={8} xxl={6}>
              <Indicateur icone={<FileDoneOutlined />} couleur={couleurObjectif(actuel.conformite, OBJECTIFS.conformite)}
                valeur={pct(actuel.conformite)} libelle="Conformité XSD" objectif={OBJECTIFS.conformite.libelle}
                detail={kpi.lotsGeneres == null ? 'Donnée non disponible' : kpi.lotsGeneres === 0 ? 'Aucun fichier généré sur la période' : `${lots} lots valides`}
                variation={<Variation actuel={actuel.conformite} precedent={avant.conformite} mois={moisAvant} sens="haut" />} />
            </Col>
            {complet && (
              <Col xs={24} sm={12} lg={8} xxl={6}>
                <Indicateur icone={<EuroOutlined />} couleur={COULEURS.or} valeur={euro.format(kpi.montantTotalVire || 0)} libelle="Montant total viré"
                  detail={kpi.fichesGenerees === 0 ? 'Aucun virement sur la période' : undefined}
                  variation={<Variation actuel={actuel.montant} precedent={avant.montant} mois={moisAvant} sens="neutre" relatif />} />
              </Col>
            )}
            <Col xs={24} sm={12} lg={8} xxl={6}>
              <Indicateur icone={<FileTextOutlined />} couleur={COULEURS.bleu} valeur={nf.format(kpi.fichesGenerees)} libelle="Fiches de paie générées" />
            </Col>
            <Col xs={24} sm={12} lg={8} xxl={6}>
              <Indicateur icone={<BankOutlined />} couleur={COULEURS.primaire} valeur={nf.format(kpi.clientsConcernes)} libelle="Clients concernés" />
            </Col>
            <Col xs={24} sm={12} lg={8} xxl={6}>
              <Indicateur icone={<AlertOutlined />} couleur={COULEURS.orange} libelle="Anomalies de la période"
                valeur={<span>{nf.format(statuts.ouvertes)}<span className="kpi-sep"> / </span>{nf.format(statuts.corrigees)}<span className="kpi-sep"> / </span>{nf.format(statuts.ignorees)}</span>}
                detail={`ouvertes / corrigées / ignorées — ${libelleMois(periodeAffichee)}`} />
            </Col>
            <Col xs={24} sm={12} lg={8} xxl={6}>
              <Indicateur icone={<RobotOutlined />} couleur={COULEURS.vert} libelle="Anomalies détectées par l'IA"
                valeur={kpiIa ? nf.format(kpiIa.anomaliesIa) : '—'}
                detail={kpiIa ? `fiches signalées par l'IA — ${libelleMois(periodeAffichee)}` : 'Donnée non disponible'} />
            </Col>
            <Col xs={24} sm={12} lg={8} xxl={6}>
              <Indicateur icone={<RobotOutlined />} couleur={COULEURS.primaire} libelle="Part détectée par l'IA seule"
                valeur={kpiIa && kpiIa.partIaSeule != null ? `${nf1.format(kpiIa.partIaSeule)} %` : '—'}
                detail={!kpiIa ? 'Donnée non disponible'
                  : kpiIa.anomaliesIa === 0 ? 'Aucune fiche signalée par l\'IA sur la période'
                    : `${nf.format(kpiIa.anomaliesIaSeule)} sur ${nf.format(kpiIa.anomaliesIa)} non vues par les contrôles métier`} />
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
          </Row>
        </div>
      )}
    </div>
  )
}
