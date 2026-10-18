import { useCallback, useEffect, useRef, useState } from 'react'
import {
  Alert, App, Button, Card, Col, Descriptions, Empty, Flex, Popconfirm, Progress, Row, Skeleton, Space,
  Statistic, Table, Tag, Tooltip, Typography,
} from 'antd'
import { CloudSyncOutlined, DatabaseOutlined, ExperimentOutlined, ReloadOutlined } from '@ant-design/icons'
import api from '../api/client'
import PageHeader from '../components/PageHeader.jsx'
import { useAuth } from '../context/AuthContext.jsx'
import { formatDate, formatPourcentage } from '../utils/format.js'

const { Text, Paragraph } = Typography

const pct = (v) => (v == null ? '—' : formatPourcentage(v * 100))

const COLONNES_METRIQUES = [
  { cle: 'precision', titre: 'Précision', aide: 'Part des alertes qui sont de vraies anomalies' },
  { cle: 'rappel', titre: 'Rappel', aide: 'Part des anomalies réelles détectées' },
  { cle: 'f1', titre: 'F1', aide: 'Moyenne harmonique de la précision et du rappel' },
  { cle: 'prAuc', titre: 'PR-AUC', aide: 'Qualité du classement des scores, adaptée aux anomalies rares' },
  { cle: 'rocAuc', titre: 'ROC-AUC', aide: 'Qualité du classement des scores' },
  { cle: 'balancedAccuracy', titre: 'Accuracy équilibrée', aide: 'Moyenne du rappel des anomalies et des paiements normaux' },
  { cle: 'accuracy', titre: 'Accuracy', aide: 'Part de décisions correctes ; trompeuse quand les anomalies sont rares' },
]

const SOURCES = { synthetique: 'données synthétiques', reel: 'données réelles' }

function MatriceConfusion({ matrice }) {
  const cellule = (valeur, libelle, type) => (
    <div className={`ia-matrice__cellule ia-matrice__cellule--${type}`}>
      <span className="ia-matrice__valeur">{valeur}</span>
      <span className="ia-matrice__libelle">{libelle}</span>
    </div>
  )
  return (
    <div className="ia-matrice">
      <div />
      <Text type="secondary" className="ia-matrice__entete">Signalé</Text>
      <Text type="secondary" className="ia-matrice__entete">Non signalé</Text>
      <Text type="secondary" className="ia-matrice__entete ia-matrice__entete--ligne">Anomalie réelle</Text>
      {cellule(matrice.vraisPositifs, 'Vrais positifs', 'ok')}
      {cellule(matrice.fauxNegatifs, 'Faux négatifs', 'ko')}
      <Text type="secondary" className="ia-matrice__entete ia-matrice__entete--ligne">Paiement normal</Text>
      {cellule(matrice.fauxPositifs, 'Faux positifs', 'warn')}
      {cellule(matrice.vraisNegatifs, 'Vrais négatifs', 'ok')}
    </div>
  )
}

export default function IntelligenceArtificielle() {
  const { message } = App.useApp()
  const { user } = useAuth()
  const estAdmin = user?.role === 'ADMINISTRATEUR'
  const [etat, setEtat] = useState(null)
  const [metriques, setMetriques] = useState(null)
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState('')
  const [entrainement, setEntrainement] = useState(null)
  const relance = useRef(null)

  const charger = useCallback(async () => {
    clearTimeout(relance.current)
    setErreur('')
    try {
      const [{ data: e }, { data: m }] = await Promise.all([api.get('/admin/ai/health'), api.get('/admin/ai/metrics')])
      setEtat(e)
      setMetriques(m)
      if (m.etat === 'EN_COURS') relance.current = setTimeout(charger, 3000)
    } catch (e) {
      setErreur(e.response?.status === 403
        ? "Votre rôle ne permet pas de consulter la supervision de l'IA."
        : 'Impossible de charger la supervision de l’IA. Réessayez dans quelques instants.')
    } finally {
      setChargement(false)
    }
  }, [])

  useEffect(() => {
    charger()
    return () => clearTimeout(relance.current)
  }, [charger])

  const reentrainer = async (source) => {
    setEntrainement(source)
    try {
      const { data } = await api.post('/admin/ai/train', null, { params: { source } })
      message.success(`${data.message} — nouvelle version ${data.modelVersion}. Évaluation en cours…`)
      await charger()
    } catch (e) {
      message.error(e.response?.data?.erreur || 'Réentraînement impossible pour le moment.')
    } finally {
      setEntrainement(null)
    }
  }

  const evaluation = metriques?.evaluation
  const modeles = evaluation?.modeles || []
  const enService = modeles.find((m) => m.enService)
  const retours = metriques?.retours

  const actions = (
    <>
      <Tooltip title="Actualiser">
        <Button icon={<ReloadOutlined />} onClick={charger} />
      </Tooltip>
      {estAdmin && (
        <>
          <Popconfirm
            title="Réentraîner sur les données synthétiques ?"
            description="Le modèle de référence est recalculé puis évalué."
            okText="Réentraîner" cancelText="Annuler"
            onConfirm={() => reentrainer('synthetique')}
          >
            <Button icon={<ExperimentOutlined />} loading={entrainement === 'synthetique'} disabled={!!entrainement || !etat?.disponible}>
              Données synthétiques
            </Button>
          </Popconfirm>
          <Popconfirm
            title="Réentraîner sur les paiements réels ?"
            description="Les paiements dont l'anomalie a été confirmée par un gestionnaire sont exclus."
            okText="Réentraîner" cancelText="Annuler"
            onConfirm={() => reentrainer('reel')}
          >
            <Button type="primary" icon={<DatabaseOutlined />} loading={entrainement === 'reel'} disabled={!!entrainement || !etat?.disponible}>
              Réentraîner sur les données réelles
            </Button>
          </Popconfirm>
        </>
      )}
    </>
  )

  const colonnes = [
    {
      title: 'Modèle',
      dataIndex: 'nom',
      fixed: 'left',
      width: 260,
      render: (nom, m) => (
        <Space direction="vertical" size={0}>
          <Text strong={m.enService}>{nom}</Text>
          {m.enService && <Tag color="blue" bordered={false}>En service</Tag>}
        </Space>
      ),
    },
    ...COLONNES_METRIQUES.map(({ cle, titre, aide }) => ({
      title: <Tooltip title={aide}>{titre}</Tooltip>,
      key: cle,
      align: 'right',
      width: 120,
      sorter: (a, b) => a.metriques[cle].moyenne - b.metriques[cle].moyenne,
      render: (_, m) => (
        <div className="ia-metrique">
          <span>{pct(m.metriques[cle].moyenne)}</span>
          <span className="ia-metrique__ecart">± {pct(m.metriques[cle].ecartType)}</span>
        </div>
      ),
    })),
    {
      title: 'Prédiction',
      key: 'temps',
      align: 'right',
      render: (_, m) => `${String(m.tempsPredictionMs).replace('.', ',')} ms`,
    },
  ]

  if (chargement) return <Card><Skeleton active paragraph={{ rows: 10 }} /></Card>

  return (
    <div className="ia-page">
      <PageHeader
        title="Intelligence artificielle"
        subtitle="Détection des montants suspects : état du modèle, performances mesurées et retours des gestionnaires."
        actions={actions}
      />

      {erreur && <Alert type="error" showIcon message={erreur} style={{ marginBottom: 16 }} />}
      {!erreur && etat && !etat.disponible && (
        <Alert type="warning" showIcon style={{ marginBottom: 16 }}
               message="Module IA indisponible"
               description="Les contrôles métier continuent de s'appliquer ; la détection IA reprendra dès que le module répondra." />
      )}

      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="État du module" valueRender={() => (
              <Tag color={etat?.disponible ? 'success' : 'error'} style={{ fontSize: 14, padding: '2px 10px' }}>
                {etat?.disponible ? 'Disponible' : 'Indisponible'}
              </Tag>
            )} />
            <Text type="secondary">Seuil d’alerte : score ≥ {String(etat?.seuilScore ?? '—').replace('.', ',')}</Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Version du modèle" value={etat?.modelVersion || '—'} valueStyle={{ fontSize: 18 }} />
            <Text type="secondary">
              {etat?.source ? `Entraîné sur ${etat.nbEntrainement} paiements (${SOURCES[etat.source] || etat.source})` : '—'}
            </Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Rappel mesuré (anomalies détectées)" value={enService ? pct(enService.metriques.rappel.moyenne) : '—'} />
            <Text type="secondary">F1 : {enService ? pct(enService.metriques.f1.moyenne) : '—'}</Text>
          </Card>
        </Col>
        <Col xs={24} sm={12} xl={6}>
          <Card>
            <Statistic title="Précision observée (retours)" value={retours?.precisionObservee != null ? pct(retours.precisionObservee) : '—'} />
            <Text type="secondary">
              {retours ? `${retours.confirmees} confirmée(s), ${retours.ignorees} ignorée(s)` : '—'}
            </Text>
          </Card>
        </Col>
      </Row>

      <Card
        title="Comparaison des modèles"
        extra={evaluation && <Text type="secondary">Évalué le {formatDate(evaluation.date)}</Text>}
        style={{ marginTop: 16 }}
      >
        {metriques?.etat === 'EN_COURS' && (
          <Alert type="info" showIcon message="Évaluation du nouveau modèle en cours, actualisation automatique." />
        )}
        {metriques?.etat === 'INDISPONIBLE' && <Empty description="Métriques indisponibles : le module IA ne répond pas." />}
        {evaluation && (
          <>
            <Paragraph type="secondary" style={{ marginBottom: 12 }}>
              Moyenne ± écart-type sur {evaluation.protocole.nbTirages} jeux de test de {evaluation.protocole.paiementsParTirage} paiements
              ({formatPourcentage(evaluation.protocole.tauxAnomalies * 100)} d’anomalies injectées). L’accuracy est peu informative quand
              les anomalies sont rares : comparer plutôt le rappel, la précision, le F1 et le PR-AUC.
            </Paragraph>
            <Table
              rowKey="cle"
              size="middle"
              columns={colonnes}
              dataSource={modeles}
              pagination={false}
              scroll={{ x: 1250 }}
              rowClassName={(m) => (m.enService ? 'ia-ligne-service' : '')}
            />
          </>
        )}
      </Card>

      {enService && (
        <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
          <Col xs={24} lg={12}>
            <Card title="Matrice de confusion — modèle en service" style={{ height: '100%' }}>
              <MatriceConfusion matrice={enService.matriceConfusion} />
              <Text type="secondary">Cumul des {evaluation.protocole.nbTirages} jeux de test.</Text>
            </Card>
          </Col>
          <Col xs={24} lg={12}>
            <Card title="Rappel par type d’anomalie" style={{ height: '100%' }}>
              {enService.rappelParType.map((t) => {
                const isole = modeles.find((m) => m.cle === 'isolation_forest')
                const rappelIsole = isole?.rappelParType.find((x) => x.type === t.type)?.rappel
                return (
                  <div key={t.type} className="ia-type">
                    <Flex justify="space-between">
                      <Text>{t.libelle}</Text>
                      <Text type="secondary">{t.detectees} / {t.total}</Text>
                    </Flex>
                    <Progress percent={Math.round((t.rappel ?? 0) * 100)} size="small"
                              status={(t.rappel ?? 0) >= 0.9 ? 'success' : 'exception'} />
                    {rappelIsole != null && rappelIsole < (t.rappel ?? 0) && (
                      <Text type="secondary" className="ia-type__note">
                        Isolation Forest seul : {pct(rappelIsole)} — complété par la règle « IBAN modifié ».
                      </Text>
                    )}
                  </div>
                )
              })}
            </Card>
          </Col>
        </Row>
      )}

      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        <Col xs={24} lg={12}>
          <Card title="Retours des gestionnaires" style={{ height: '100%' }}>
            {retours && (
              <>
                <Row gutter={16}>
                  <Col span={8}><Statistic title="Confirmées" value={retours.confirmees} /></Col>
                  <Col span={8}><Statistic title="Ignorées" value={retours.ignorees} /></Col>
                  <Col span={8}><Statistic title="En attente" value={retours.enAttente} /></Col>
                </Row>
                <Paragraph type="secondary" style={{ marginTop: 16, marginBottom: 0 }}>
                  Chaque montant suspect marqué « Corrigée » (anomalie confirmée) ou « Ignorée » (fausse alerte) dans la page
                  Anomalies alimente la précision observée. Au réentraînement sur données réelles, les paiements confirmés
                  anormaux sont exclus : le modèle apprend la normalité à partir de données vérifiées.
                </Paragraph>
              </>
            )}
          </Card>
        </Col>
        <Col xs={24} lg={12}>
          <Card title={<Space><CloudSyncOutlined />Paramètres et protocole</Space>} style={{ height: '100%' }}>
            {evaluation ? (
              <Descriptions column={1} size="small" labelStyle={{ width: 190 }}>
                <Descriptions.Item label="Algorithme">{evaluation.configuration.algorithme}</Descriptions.Item>
                <Descriptions.Item label="Seuil de score">{String(evaluation.configuration.seuilScore).replace('.', ',')}</Descriptions.Item>
                <Descriptions.Item label="Contamination">{formatPourcentage(evaluation.configuration.contamination * 100)}</Descriptions.Item>
                <Descriptions.Item label="Nombre d’arbres">{evaluation.configuration.nbArbres}</Descriptions.Item>
                <Descriptions.Item label="Historique">{evaluation.configuration.historiqueMois} mois</Descriptions.Item>
                <Descriptions.Item label="Variables">{evaluation.configuration.variables.join(', ')}</Descriptions.Item>
                <Descriptions.Item label="Règle de décision">{evaluation.protocole.regleDecision}</Descriptions.Item>
                <Descriptions.Item label="Jeu de test">{evaluation.protocole.jeuTest}</Descriptions.Item>
              </Descriptions>
            ) : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Disponible après l’évaluation du modèle" />}
          </Card>
        </Col>
      </Row>
    </div>
  )
}
