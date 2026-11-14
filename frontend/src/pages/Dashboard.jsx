import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert, Card, Col, Row, Skeleton, Table, Tag, Typography } from 'antd'
import {
  AlertOutlined,
  CheckSquareOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  EditOutlined,
  FileExcelOutlined,
  RightOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import api from '../api/client'
import PageHeader from '../components/PageHeader.jsx'
import { estAdmin, peutValider, peutVoirKpi } from '../constants/roles.js'
import { useAuth } from '../context/AuthContext.jsx'
import { formatMontant } from '../utils/format.js'

const nf = new Intl.NumberFormat('fr-FR')
const COULEURS = { vert: '#2E7D32', orange: '#D97706', rouge: '#C62828', bleu: '#2D5283', gris: '#8C99AB' }

/** Carte « à faire » : cliquable vers la page concernée ; vert quand il n'y a rien à traiter. */
function CarteAction({ icone, libelle, valeur, aide, urgence, vers }) {
  const navigate = useNavigate()
  const indisponible = valeur == null
  const couleur = indisponible ? COULEURS.gris : valeur === 0 ? COULEURS.vert : COULEURS[urgence]
  return (
    <Card
      hoverable
      onClick={() => navigate(vers)}
      style={{ height: '100%', borderTop: `3px solid ${couleur}`, cursor: 'pointer' }}
      styles={{ body: { padding: 18 } }}
    >
      <div className="kpi-carte__tete">
        <span className="kpi-carte__icone" style={{ background: `${couleur}1A`, color: couleur }}>{icone}</span>
        <Typography.Text type="secondary">{libelle}</Typography.Text>
      </div>
      <div className="kpi-carte__valeur" style={{ color: couleur }}>{indisponible ? '—' : nf.format(valeur)}</div>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>
        {indisponible ? 'Donnée non disponible' : valeur === 0 ? 'Rien à traiter' : aide}
        {!indisponible && valeur > 0 && <RightOutlined style={{ marginLeft: 6, fontSize: 10 }} />}
      </Typography.Text>
    </Card>
  )
}

const compter = (resultat, filtre) => (resultat.status === 'fulfilled' ? resultat.value.data.filter(filtre).length : null)

function DashboardOperationnel() {
  const { user } = useAuth()
  const [donnees, setDonnees] = useState(null)

  useEffect(() => {
    // Chaque source est indépendante : l'échec de l'une n'empêche pas l'affichage des autres.
    Promise.allSettled([
      api.get('/masse/fichiers', { silent: true }),
      api.get('/anomalies', { params: { statut: 'OUVERTE' }, silent: true }),
      api.get('/transactions', { silent: true }),
      api.get('/dashboard/kpi', { params: { periode: dayjs().format('YYYY-MM') }, silent: true }),
    ]).then(([fichiers, anomalies, transactions, kpi]) => setDonnees({ fichiers, anomalies, transactions, kpi }))
  }, [])

  const manager = peutValider(user?.role)
  const moisCourant = dayjs().format('MMMM YYYY')

  if (!donnees) {
    return (
      <div>
        <PageHeader title="Tableau de bord" subtitle="Chargement…" />
        <Skeleton active paragraph={{ rows: 6 }} style={{ marginTop: 24 }} />
      </div>
    )
  }

  const { fichiers, anomalies, transactions, kpi } = donnees
  const aTraiter = [
    ...(manager
      ? [{
        cle: 'valider', icone: <CheckSquareOutlined />, libelle: 'Fichiers à valider', urgence: 'orange',
        valeur: compter(fichiers, (f) => f.statutFichier === 'SOUMIS'), aide: 'Fichiers soumis par les Experts RH', vers: '/virements-masse/a-valider',
      }]
      : [
        {
          cle: 'rejetes', icone: <FileExcelOutlined />, libelle: 'Fichiers rejetés à corriger', urgence: 'rouge',
          valeur: compter(fichiers, (f) => f.statutFichier === 'REJETE'), aide: 'Voir le motif et relancer un contrôle', vers: '/virements-masse/historique',
        },
        {
          cle: 'brouillons', icone: <EditOutlined />, libelle: 'Brouillons à soumettre', urgence: 'orange',
          valeur: compter(fichiers, (f) => f.statutFichier === 'BROUILLON' && f.valideXsd), aide: 'Fichiers conformes pas encore soumis', vers: '/virements-masse/historique',
        },
      ]),
    {
      cle: 'anomalies', icone: <AlertOutlined />, libelle: 'Anomalies ouvertes', urgence: 'rouge',
      valeur: anomalies.status === 'fulfilled' ? anomalies.value.data.length : null, aide: 'À corriger ou ignorer', vers: '/anomalies',
    },
    {
      cle: 'attente', icone: <ClockCircleOutlined />, libelle: 'Transactions en attente', urgence: 'orange',
      valeur: compter(transactions, (t) => t.statut === 'BROUILLON' || t.statut === 'VALIDEE'),
      aide: manager ? 'À approuver' : 'Pas encore approuvées', vers: '/transactions',
    },
    {
      cle: 'rejetees', icone: <CloseCircleOutlined />, libelle: 'Transactions rejetées', urgence: 'rouge',
      valeur: compter(transactions, (t) => t.statut === 'REJETEE'), aide: 'À revoir', vers: '/transactions',
    },
  ]
  const indisponibles = [fichiers, anomalies, transactions].some((r) => r.status === 'rejected')
  const k = kpi.status === 'fulfilled' ? kpi.value.data : null

  return (
    <div>
      <PageHeader
        title="Tableau de bord"
        subtitle={`Ce qui demande une action — situation au ${dayjs().format('DD/MM/YYYY')}`}
      />

      <Alert
        type="info"
        showIcon
        style={{ margin: '16px 0', background: '#EEF3FA', borderColor: '#D6E0EF' }}
        message={`Les éléments à traiter sont comptés à date. Les chiffres du mois portent sur ${moisCourant}.`}
        action={peutVoirKpi(user?.role) && <Link to="/kpi">Voir les indicateurs</Link>}
      />
      {indisponibles && (
        <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="Certaines données n'ont pas pu être chargées : les cartes concernées affichent « — »." />
      )}

      <Typography.Title level={5} style={{ margin: '8px 0 12px' }}>À traiter maintenant</Typography.Title>
      <Row gutter={[16, 16]}>
        {aTraiter.map(({ cle, ...carte }) => (
          <Col xs={24} sm={12} lg={8} key={cle}><CarteAction {...carte} /></Col>
        ))}
      </Row>

      <Typography.Title level={5} style={{ margin: '28px 0 12px' }}>
        Ce mois-ci <Tag style={{ marginLeft: 8 }}>{moisCourant}</Tag>
      </Typography.Title>
      <Row gutter={[16, 16]}>
        {[
          { libelle: 'Fiches approuvées', valeur: k ? nf.format(k.fichesGenerees) : '—' },
          { libelle: 'Fichiers SEPA générés', valeur: k && k.lotsGeneres != null ? nf.format(k.lotsGeneres) : '—' },
          { libelle: "Taux d'erreur", valeur: k ? `${nf.format(Number(k.tauxErreur))} %` : '—' },
        ].map((s) => (
          <Col xs={24} sm={8} key={s.libelle}>
            <Card styles={{ body: { padding: 16 } }}>
              <Typography.Text type="secondary">{s.libelle}</Typography.Text>
              <div style={{ fontSize: 24, fontWeight: 700, color: '#1F3A5F' }}>{s.valeur}</div>
            </Card>
          </Col>
        ))}
      </Row>
    </div>
  )
}

const VERS_HISTORIQUE = '/virements-masse/historique'

/** Vue globale de l'administrateur : chiffres agrégés uniquement, jamais de donnée par salarié. */
function DashboardAdmin() {
  const [donnees, setDonnees] = useState(null)

  useEffect(() => {
    Promise.allSettled([
      api.get('/masse/fichiers', { silent: true }),
      api.get('/dashboard/kpi', { params: { periode: dayjs().format('YYYY-MM') }, silent: true }),
    ]).then(([fichiers, kpi]) => setDonnees({ fichiers, kpi }))
  }, [])

  const moisCourant = dayjs().format('MMMM YYYY')

  if (!donnees) {
    return (
      <div>
        <PageHeader title="Tableau de bord" subtitle="Chargement…" />
        <Skeleton active paragraph={{ rows: 6 }} style={{ marginTop: 24 }} />
      </div>
    )
  }

  const { fichiers, kpi } = donnees
  const liste = fichiers.status === 'fulfilled' ? fichiers.value.data : null
  const k = kpi.status === 'fulfilled' ? kpi.value.data : null
  const compterStatut = (statut) => (liste ? liste.filter((f) => f.statutFichier === statut).length : null)

  const parSociete = liste
    ? Object.values(liste.reduce((acc, f) => {
      const cle = f.clientId || '—'
      const ligne = acc[cle] || (acc[cle] = { societe: cle, fichiers: 0, salaries: 0, montant: 0, nonConformes: 0 })
      ligne.fichiers += 1
      ligne.salaries += f.nbTransactions || 0
      ligne.montant += Number(f.ctrlSum) || 0
      ligne.nonConformes += f.valideXsd ? 0 : 1
      return acc
    }, {})).sort((a, b) => b.montant - a.montant)
    : []

  const cartes = [
    { cle: 'soumis', icone: <ClockCircleOutlined />, libelle: 'Fichiers en attente de validation', urgence: 'orange', valeur: compterStatut('SOUMIS'), aide: 'Soumis, en attente du Manager', vers: VERS_HISTORIQUE },
    { cle: 'rejetes', icone: <FileExcelOutlined />, libelle: 'Fichiers rejetés', urgence: 'rouge', valeur: compterStatut('REJETE'), aide: 'Retournés aux Experts RH', vers: VERS_HISTORIQUE },
    {
      cle: 'xsd', icone: <CloseCircleOutlined />, libelle: 'Fichiers non conformes au XSD', urgence: 'rouge',
      valeur: liste ? liste.filter((f) => !f.valideXsd).length : null, aide: 'Voir l’historique', vers: VERS_HISTORIQUE,
    },
  ]

  return (
    <div>
      <PageHeader title="Tableau de bord" subtitle={`Vue globale du système — situation au ${dayjs().format('DD/MM/YYYY')}`} />

      <Alert
        type="info"
        showIcon
        style={{ margin: '16px 0', background: '#EEF3FA', borderColor: '#D6E0EF' }}
        message={`Statistiques globales, sans donnée individuelle de paie. Fichiers : tout l'historique ; chiffres du mois : ${moisCourant}.`}
        action={<Link to="/kpi">Voir les indicateurs</Link>}
      />
      {(!liste || !k) && (
        <Alert type="warning" showIcon style={{ marginBottom: 16 }} message="Certaines données n'ont pas pu être chargées : les cartes concernées affichent « — »." />
      )}

      <Typography.Title level={5} style={{ margin: '8px 0 12px' }}>Supervision des fichiers</Typography.Title>
      <Row gutter={[16, 16]}>
        {cartes.map(({ cle, ...carte }) => (
          <Col xs={24} sm={12} lg={8} key={cle}><CarteAction {...carte} /></Col>
        ))}
      </Row>

      <Typography.Title level={5} style={{ margin: '28px 0 12px' }}>
        Ce mois-ci <Tag style={{ marginLeft: 8 }}>{moisCourant}</Tag>
      </Typography.Title>
      <Row gutter={[16, 16]}>
        {[
          { libelle: 'Fiches approuvées', valeur: k ? nf.format(k.fichesGenerees) : '—' },
          { libelle: 'Fichiers SEPA générés', valeur: k && k.lotsGeneres != null ? nf.format(k.lotsGeneres) : '—' },
          { libelle: "Taux d'erreur", valeur: k ? `${nf.format(Number(k.tauxErreur))} %` : '—' },
          { libelle: 'Montant total viré', valeur: k ? formatMontant(k.montantTotalVire) : '—' },
        ].map((s) => (
          <Col xs={24} sm={12} lg={6} key={s.libelle}>
            <Card styles={{ body: { padding: 16 } }}>
              <Typography.Text type="secondary">{s.libelle}</Typography.Text>
              <div style={{ fontSize: 24, fontWeight: 700, color: '#1F3A5F' }}>{s.valeur}</div>
            </Card>
          </Col>
        ))}
      </Row>

      <Typography.Title level={5} style={{ margin: '28px 0 12px' }}>Par société</Typography.Title>
      <Table
        size="small"
        rowKey="societe"
        pagination={{ pageSize: 10, hideOnSinglePage: true }}
        dataSource={parSociete}
        locale={{ emptyText: liste ? 'Aucun fichier SEPA enregistré.' : 'Donnée non disponible' }}
        columns={[
          { title: 'Société', dataIndex: 'societe' },
          { title: 'Fichiers', dataIndex: 'fichiers', align: 'right' },
          { title: 'Salariés (cumul)', dataIndex: 'salaries', align: 'right' },
          { title: 'Montant total', dataIndex: 'montant', align: 'right', render: formatMontant },
          { title: 'Non conformes XSD', dataIndex: 'nonConformes', align: 'right', render: (n) => (n > 0 ? <Tag color="red">{n}</Tag> : <Tag color="green">0</Tag>) },
        ]}
      />
    </div>
  )
}

export default function Dashboard() {
  const { user } = useAuth()
  return estAdmin(user?.role) ? <DashboardAdmin /> : <DashboardOperationnel />
}
