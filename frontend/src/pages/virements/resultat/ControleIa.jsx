import { useState } from 'react'
import { Alert, Progress, Space, Switch, Table, Tag, Typography } from 'antd'
import { useOutletContext } from 'react-router-dom'
import { formatMontant } from '../../../utils/format.js'

const couleurScore = (score) => (score >= 0.85 ? '#cf1322' : score >= 0.6 ? '#d46b08' : score >= 0.35 ? '#faad14' : '#52c41a')

export default function ControleIa() {
  const { resultat } = useOutletContext()
  const { ia } = resultat
  const [suspectsSeulement, setSuspectsSeulement] = useState(false)

  if (!ia.disponible) {
    return (
      <Alert
        type="info"
        showIcon
        message="Analyse IA indisponible"
        description="Le module IA est désactivé, injoignable, ou n’a pas été exécuté pour ce fichier. Les contrôles métier et la validation XSD restent valables."
      />
    )
  }

  const lignes = suspectsSeulement ? ia.lignes.filter((l) => l.suspect) : ia.lignes

  const colonnes = [
    { title: 'Matricule', dataIndex: 'matricule', sorter: (a, b) => String(a.matricule).localeCompare(String(b.matricule)) },
    { title: 'Nom', dataIndex: 'nom' },
    { title: 'Montant', dataIndex: 'montant', align: 'right', render: formatMontant, sorter: (a, b) => Number(a.montant) - Number(b.montant) },
    {
      title: 'Score',
      dataIndex: 'score',
      width: 180,
      defaultSortOrder: 'descend',
      sorter: (a, b) => (a.score ?? -1) - (b.score ?? -1),
      render: (score) => (score == null
        ? '—'
        : <Progress percent={Math.round(score * 100)} size="small" strokeColor={couleurScore(score)} format={() => score.toFixed(2).replace('.', ',')} />),
    },
    {
      title: 'Suspect',
      dataIndex: 'suspect',
      width: 100,
      render: (suspect) => (suspect ? <Tag color="orange">Suspect</Tag> : <Tag color="green">Normal</Tag>),
    },
    {
      title: 'Raisons',
      dataIndex: 'raisons',
      render: (raisons) => (raisons?.length ? raisons.join(' ; ') : '—'),
    },
    { title: 'Recommandation', dataIndex: 'recommandation', render: (r) => r || '—' },
  ]

  return (
    <div>
      <Space wrap style={{ marginBottom: 12, justifyContent: 'space-between', width: '100%' }}>
        <Typography.Text>
          <strong>{ia.nbSuspects}</strong> montant(s) suspect(s) sur {ia.nbAnalyses} paiement(s) analysé(s)
          {ia.modelVersion ? ` — modèle ${ia.modelVersion}` : ''}
        </Typography.Text>
        <Space>
          <Switch checked={suspectsSeulement} onChange={setSuspectsSeulement} />
          <span>Suspects uniquement</span>
        </Space>
      </Space>
      <Table
        rowKey="transactionId"
        size="small"
        columns={colonnes}
        dataSource={lignes}
        pagination={{ pageSize: 15, hideOnSinglePage: true }}
        rowClassName={(l) => (l.suspect ? 'row-suspect' : '')}
        locale={{ emptyText: suspectsSeulement ? 'Aucun montant suspect.' : 'Aucun paiement analysé.' }}
      />
    </div>
  )
}
