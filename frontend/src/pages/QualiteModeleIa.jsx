import { useEffect, useState } from 'react'
import { Alert, Card, Col, Descriptions, Empty, Progress, Row, Skeleton, Space, Table, Tag, Tooltip, Typography } from 'antd'
import dayjs from 'dayjs'
import api from '../api/client'
import PageHeader from '../components/PageHeader.jsx'

const nf = new Intl.NumberFormat('fr-FR')
const dec = (v, d = 2) => new Intl.NumberFormat('fr-FR', { minimumFractionDigits: d, maximumFractionDigits: d }).format(v)
const COULEURS = { primaire: '#1F3A5F', bleu: '#2D5283', vert: '#2E7D32', orange: '#D97706', rouge: '#C62828', gris: '#8C99AB' }

const couleurRappel = (r) => (r >= 0.8 ? COULEURS.vert : r >= 0.4 ? COULEURS.orange : COULEURS.rouge)

/** Carte métrique : valeur moyenne et écart-type sur les graines du banc d'essai. */
function Metrique({ libelle, stat, pourcent = false, decimales = 2, aide }) {
  const valeur = stat == null ? '—' : pourcent ? `${dec(stat.moyenne * 100, 0)} %` : dec(stat.moyenne, decimales)
  const ecart = stat == null ? null : pourcent ? `± ${dec(stat.ecartType * 100, 0)} pts` : `± ${dec(stat.ecartType, decimales)}`
  return (
    <Card styles={{ body: { padding: 16 } }} style={{ height: '100%', borderTop: `3px solid ${COULEURS.bleu}` }}>
      <Tooltip title={aide}><Typography.Text type="secondary">{libelle}</Typography.Text></Tooltip>
      <div style={{ fontSize: 26, fontWeight: 700, color: COULEURS.primaire }}>{valeur}</div>
      <Typography.Text type="secondary" style={{ fontSize: 12 }}>{ecart}</Typography.Text>
    </Card>
  )
}

/** Courbe précision–rappel (moyenne des graines) avec le point de fonctionnement actuel. */
function CourbePrecisionRappel({ courbe, point }) {
  if (!courbe?.rappel?.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Courbe indisponible" />
  const L = 560, H = 360, g = 54, d = 20, b = 46, h = 16
  const x = (r) => g + r * (L - g - d)
  const y = (p) => H - b - p * (H - b - h)
  const trace = courbe.rappel.map((r, i) => `${i === 0 ? 'M' : 'L'}${x(r).toFixed(1)},${y(courbe.precision[i]).toFixed(1)}`).join(' ')
  const graduations = [0, 0.2, 0.4, 0.6, 0.8, 1]
  return (
    <svg viewBox={`0 0 ${L} ${H}`} width="100%" role="img" aria-label="Courbe précision-rappel du modèle en service">
      {graduations.map((v) => (
        <g key={v}>
          <line x1={g} x2={L - d} y1={y(v)} y2={y(v)} stroke="#E6EAF0" />
          <text x={g - 8} y={y(v) + 4} fontSize="11" textAnchor="end" fill="#667085">{dec(v, 1)}</text>
          <line y1={h} y2={H - b} x1={x(v)} x2={x(v)} stroke="#F0F2F6" />
          <text x={x(v)} y={H - b + 16} fontSize="11" textAnchor="middle" fill="#667085">{dec(v, 1)}</text>
        </g>
      ))}
      <path d={trace} fill="none" stroke={COULEURS.primaire} strokeWidth="2.5" />
      {point && (
        <g>
          <circle cx={x(point.rappel)} cy={y(point.precision)} r="7" fill={COULEURS.orange} stroke="#fff" strokeWidth="2" />
          <text x={x(point.rappel) + 12} y={y(point.precision) - 8} fontSize="12" fill={COULEURS.orange}>Seuil actuel</text>
        </g>
      )}
      <text x={(g + L - d) / 2} y={H - 8} fontSize="12" textAnchor="middle" fill="#344054">Rappel</text>
      <text transform={`translate(14,${(h + H - b) / 2}) rotate(-90)`} fontSize="12" textAnchor="middle" fill="#344054">Précision</text>
    </svg>
  )
}

export default function QualiteModeleIa() {
  const [donnees, setDonnees] = useState(null)
  const [erreur, setErreur] = useState('')

  useEffect(() => {
    api.get('/admin/ai/qualite', { silent: true })
      .then(({ data }) => setDonnees(data))
      .catch(() => setErreur('Impossible de charger la qualité du modèle IA.'))
  }, [])

  if (erreur) {
    return (
      <div>
        <PageHeader title="Qualité du modèle IA" />
        <Alert type="error" showIcon message={erreur} style={{ marginTop: 16 }} />
      </div>
    )
  }
  if (!donnees) {
    return (
      <div>
        <PageHeader title="Qualité du modèle IA" subtitle="Chargement…" />
        <Skeleton active paragraph={{ rows: 8 }} style={{ marginTop: 24 }} />
      </div>
    )
  }

  const { modele, banc, bancDisponible, dateBanc } = donnees
  const evaluation = banc?.evaluation
  const service = evaluation?.configurations?.en_service
  const m = service?.metriques
  const parType = service?.rappelParType || []
  const comparaison = banc?.comparaison
  const taille = modele.nbEntrainement > 0 ? `${nf.format(modele.nbEntrainement)} fiches` : 'Non renseignée'
  const tailleBanc = evaluation?.modele?.nbEntrainement ? `${nf.format(Math.round(evaluation.modele.nbEntrainement))} fiches` : null

  return (
    <div>
      <PageHeader
        title="Qualité du modèle IA"
        subtitle="Supervision technique : version, jeu d’entraînement et résultats du dernier banc d’essai — aucune donnée de salarié."
        actions={[modele.disponible
          ? <Tag key="etat" color="green">Module IA disponible</Tag>
          : <Tag key="etat" color="red">Module IA indisponible</Tag>]}
      />

      <Card title="Modèle en service" style={{ margin: '16px 0' }}>
        <Descriptions bordered size="small" column={{ xs: 1, md: 2 }} styles={{ label: { width: 190 } }}>
          <Descriptions.Item label="Version du modèle">{modele.modelVersion || '—'}</Descriptions.Item>
          <Descriptions.Item label="Date d’entraînement">
            <span style={{ whiteSpace: 'nowrap' }}>{modele.dateEntrainement ? dayjs(modele.dateEntrainement).format('DD/MM/YYYY') : '—'}</span>
          </Descriptions.Item>
          <Descriptions.Item label="Taille du jeu d’entraînement">
            {taille}
            {tailleBanc && <Typography.Text type="secondary"> — banc d’essai : {tailleBanc}</Typography.Text>}
          </Descriptions.Item>
          <Descriptions.Item label="Origine des données">{modele.source ? { synthetique: 'Synthétique', reel: 'Paiements réels' }[modele.source] || modele.source : '—'}</Descriptions.Item>
          <Descriptions.Item label="Seuil utilisé" span={2}>
            <strong>Score ≥ {dec(evaluation?.seuilEnService ?? 0.5, 1)}</strong> ou IBAN modifié
            <div>
              <Typography.Text type="secondary" style={{ fontSize: 12 }}>
                Paramètre configuré : {dec(modele.seuilScore ?? 0.6, 1)}. Il est sans effet tant que la prédiction native de
                l’Isolation Forest signale déjà tout score supérieur à 0,5 (constat du banc d’essai).
              </Typography.Text>
            </div>
          </Descriptions.Item>
        </Descriptions>
      </Card>

      {!bancDisponible && (
        <Alert
          type="info"
          showIcon
          message="Résultats du banc d’essai indisponibles"
          description="Le fichier resultats.json n’a pas été trouvé. Lancez le banc d’essai (ai-module/benchmark) puis actualisez cette page."
        />
      )}

      {bancDisponible && service && (
        <>
          <Alert
            type="warning"
            showIcon
            style={{ marginBottom: 16 }}
            message={`Dernier banc d’essai${dateBanc ? ` — ${dayjs(dateBanc).format('DD/MM/YYYY HH:mm')}` : ''} : données synthétiques`}
            description={`Jeu de test (mois 16 à 18, jamais utilisé pour l’entraînement ni le choix du seuil), moyenne ± écart-type sur ${evaluation.protocole.graines.length} graines. `
              + 'Ces résultats mesurent la capacité à retrouver des anomalies injectées, pas la performance sur de vraies paies.'}
          />

          <Typography.Title level={5} style={{ margin: '8px 0 12px' }}>Performances sur le jeu de test</Typography.Title>
          <Row gutter={[16, 16]}>
            <Col xs={12} md={8} xl={4}><Metrique libelle="Précision" stat={m.precision} pourcent aide="Part des fiches signalées qui sont de vraies anomalies" /></Col>
            <Col xs={12} md={8} xl={4}><Metrique libelle="Rappel" stat={m.rappel} pourcent aide="Part des anomalies réelles qui sont détectées" /></Col>
            <Col xs={12} md={8} xl={4}><Metrique libelle="F1" stat={m.f1} aide="Moyenne harmonique de la précision et du rappel" /></Col>
            <Col xs={12} md={8} xl={4}><Metrique libelle="PR-AUC" stat={m.prAuc} aide="Aire sous la courbe précision–rappel : mesure la plus pertinente en données déséquilibrées" /></Col>
            <Col xs={12} md={8} xl={4}><Metrique libelle="ROC-AUC" stat={m.rocAuc} /></Col>
            <Col xs={12} md={8} xl={4}><Metrique libelle="Faux positifs / 1 000 fiches" stat={m.fpPour1000} decimales={1} aide="Charge de vérification pour l’Expert RH" /></Col>
          </Row>

          <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
            <Col xs={24} xl={12}>
              <Card title="Détection par type d’anomalie" style={{ height: '100%' }}>
                <Table
                  size="small"
                  rowKey="type"
                  pagination={false}
                  dataSource={parType}
                  columns={[
                    { title: 'Type d’anomalie', dataIndex: 'libelle' },
                    {
                      title: 'Rappel',
                      dataIndex: 'rappel',
                      width: 220,
                      render: (r) => (
                        <Space size={6} style={{ width: '100%' }}>
                          <Progress percent={Math.round(r.moyenne * 100)} size="small" showInfo={false}
                            strokeColor={couleurRappel(r.moyenne)} style={{ width: 120, margin: 0 }} />
                          <span>{dec(r.moyenne * 100, 0)} %</span>
                        </Space>
                      ),
                    },
                  ]}
                />
                <Typography.Paragraph type="secondary" style={{ fontSize: 12, margin: '12px 0 0' }}>
                  Salarié fantôme et salarié sorti encore payé ne sont pas détectés : le modèle n’a pas l’historique de présence du salarié.
                </Typography.Paragraph>
              </Card>
            </Col>
            <Col xs={24} xl={12}>
              <Card title="Courbe précision–rappel" style={{ height: '100%' }}>
                <CourbePrecisionRappel
                  courbe={evaluation.courbePrecisionRappel}
                  point={{ rappel: m.rappel.moyenne, precision: m.precision.moyenne }}
                />
              </Card>
            </Col>
          </Row>

          {comparaison && (
            <Card title="Comparaison des approches" style={{ marginTop: 16 }}>
              <Table
                size="small"
                rowKey="cle"
                pagination={false}
                dataSource={Object.entries(comparaison).map(([cle, a]) => ({ cle, nom: a.nom, ...a.metriques }))}
                columns={[
                  { title: 'Approche', dataIndex: 'nom' },
                  { title: 'F1', dataIndex: 'f1', align: 'right', render: (s) => dec(s.moyenne) },
                  { title: 'PR-AUC', dataIndex: 'prAuc', align: 'right', render: (s) => dec(s.moyenne) },
                  { title: 'Faux positifs / 1 000', dataIndex: 'fpPour1000', align: 'right', render: (s) => dec(s.moyenne, 1) },
                ]}
              />
              {banc.candidat && (
                <Typography.Paragraph type="secondary" style={{ fontSize: 12, margin: '12px 0 0' }}>
                  Version candidate « {banc.candidat.version} » préparée mais <strong>non activée</strong> : le modèle en service reste inchangé.
                </Typography.Paragraph>
              )}
            </Card>
          )}
        </>
      )}
    </div>
  )
}
