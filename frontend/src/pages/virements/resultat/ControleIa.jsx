import { useMemo, useState } from 'react'
import { Alert, Card, Progress, Segmented, Space, Switch, Table, Tag, Typography } from 'antd'
import { useOutletContext } from 'react-router-dom'
import { formatMontant } from '../../../utils/format.js'
import { NIVEAUX, niveauDe, repartitionParType } from './ia.js'

const couleurScore = (score) => (score >= 0.85 ? '#cf1322' : score >= 0.6 ? '#d46b08' : score >= 0.35 ? '#faad14' : '#52c41a')
const pourcent = new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 1 })

export default function ControleIa() {
  const { resultat } = useOutletContext()
  const { ia } = resultat
  const [suspectsSeulement, setSuspectsSeulement] = useState(true)
  const [niveau, setNiveau] = useState('TOUS')

  const suspects = useMemo(() => (ia.lignes || []).filter((l) => l.suspect), [ia.lignes])
  const parNiveau = useMemo(() => {
    const compte = { ELEVE: 0, MOYEN: 0, FAIBLE: 0 }
    suspects.forEach((l) => { const n = niveauDe(l); if (n) compte[n] += 1 })
    return compte
  }, [suspects])
  const parType = useMemo(() => repartitionParType(ia.lignes || []), [ia.lignes])

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

  const lignes = (suspectsSeulement ? suspects : ia.lignes)
    .filter((l) => niveau === 'TOUS' || niveauDe(l) === niveau)

  const colonnes = [
    {
      title: 'Salarié',
      dataIndex: 'nom',
      width: 160,
      sorter: (a, b) => String(a.matricule).localeCompare(String(b.matricule)),
      render: (nom, l) => (
        <div>
          <div>{nom}</div>
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>{l.matricule}</Typography.Text>
        </div>
      ),
    },
    { title: 'Montant', dataIndex: 'montant', align: 'right', width: 110, render: formatMontant, sorter: (a, b) => Number(a.montant) - Number(b.montant) },
    {
      title: 'Score et niveau',
      dataIndex: 'score',
      width: 180,
      defaultSortOrder: 'descend',
      sorter: (a, b) => (a.score ?? -1) - (b.score ?? -1),
      render: (score, l) => {
        if (score == null) return '—'
        const n = NIVEAUX[niveauDe(l)]
        return (
          <Space size={8}>
            <Progress percent={Math.round(score * 100)} size="small" strokeColor={couleurScore(score)}
              format={() => score.toFixed(2).replace('.', ',')} style={{ width: 90, margin: 0 }} />
            {n && <Tag color={n.color} style={{ margin: 0 }}>{n.label}</Tag>}
          </Space>
        )
      },
    },
    {
      title: 'Type probable',
      dataIndex: 'typeProbable',
      width: 170,
      render: (t, l) => (l.suspect ? (t ? <Tag color="geekblue" style={{ whiteSpace: 'normal' }}>{t}</Tag> : '—') : '—'),
    },
    {
      title: 'Explication',
      dataIndex: 'raisons',
      render: (raisons) => (raisons?.length
        ? <ul style={{ margin: 0, paddingLeft: 16 }}>{raisons.map((r) => <li key={r}>{r}</li>)}</ul>
        : '—'),
    },
    { title: 'Recommandation', dataIndex: 'recommandation', width: 230, render: (r) => r || '—' },
  ]

  return (
    <div>
      <Card styles={{ body: { padding: 16 } }} style={{ marginBottom: 16 }}>
        <Space direction="vertical" size={8} style={{ width: '100%' }}>
          <Typography.Text>
            <strong>{ia.nbSuspects}</strong> fiche(s) suspecte(s) sur {ia.nbAnalyses} analysée(s)
            {ia.nbAnalyses > 0 ? ` (${pourcent.format((ia.nbSuspects / ia.nbAnalyses) * 100)} %)` : ''}
            {ia.modelVersion ? ` — modèle ${ia.modelVersion}` : ''}
          </Typography.Text>
          <Space wrap size={[16, 4]}>
            {Object.entries(NIVEAUX).map(([cle, n]) => (
              <span key={cle}><Tag color={n.color}>{n.label}</Tag>{parNiveau[cle]}</span>
            ))}
          </Space>
          {parType.length > 0 && (
            <Space wrap size={[8, 4]}>
              <Typography.Text type="secondary">Types probables :</Typography.Text>
              {parType.map((p) => <Tag key={p.type} color="geekblue">{p.type} × {p.nombre}</Tag>)}
            </Space>
          )}
        </Space>
      </Card>

      <Space wrap style={{ marginBottom: 12, justifyContent: 'space-between', width: '100%' }}>
        <Space>
          <span>Niveau :</span>
          <Segmented
            value={niveau}
            onChange={setNiveau}
            options={[{ label: 'Tous', value: 'TOUS' }, ...Object.entries(NIVEAUX).map(([value, n]) => ({ label: n.label, value }))]}
          />
        </Space>
        <Space>
          <Switch checked={suspectsSeulement} onChange={setSuspectsSeulement} />
          <span>Fiches suspectes uniquement</span>
        </Space>
      </Space>
      <Table
        rowKey="transactionId"
        size="small"
        columns={colonnes}
        dataSource={lignes}
        pagination={{ pageSize: 15, hideOnSinglePage: true }}
        scroll={{ x: 900 }}
        rowClassName={(l) => (l.suspect ? 'row-suspect' : '')}
        locale={{ emptyText: suspectsSeulement ? 'Aucune fiche suspecte pour ce filtre.' : 'Aucun paiement analysé.' }}
      />
    </div>
  )
}
