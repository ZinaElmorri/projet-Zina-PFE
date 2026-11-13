import { useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Alert, Button, Input, Select, Space, Table, Tag } from 'antd'
import api from '../../api/client'
import ActionButton from '../../components/ActionButton.jsx'
import PageHeader from '../../components/PageHeader.jsx'
import { formatDate, formatMontant } from '../../utils/format.js'
import { BASE, messageEchec, STATUTS_FICHIER, telechargerFichier, urlResultat } from './parcours.js'

export default function Historique() {
  const navigate = useNavigate()
  const [fichiers, setFichiers] = useState([])
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState('')
  const [recherche, setRecherche] = useState('')
  const [statut, setStatut] = useState(null)

  useEffect(() => {
    api.get('/masse/fichiers', { silent: true })
      .then(({ data }) => setFichiers(data))
      .catch((e) => setErreur(messageEchec(e)))
      .finally(() => setChargement(false))
  }, [])

  const lignes = useMemo(() => {
    const terme = recherche.trim().toLowerCase()
    return fichiers
      .filter((f) => !statut || f.statutFichier === statut)
      .filter((f) => !terme || `${f.nomFichier} ${f.clientId || ''} ${f.periode || ''} ${f.generePar || ''}`.toLowerCase().includes(terme))
  }, [fichiers, recherche, statut])

  const colonnes = [
    { title: 'Date', dataIndex: 'genereLe', render: formatDate, sorter: (a, b) => String(a.genereLe).localeCompare(String(b.genereLe)), defaultSortOrder: 'descend' },
    { title: 'Société', dataIndex: 'clientId', render: (c) => c || '—' },
    { title: 'Période', dataIndex: 'periode', render: (p) => p || '—' },
    { title: 'Salariés', dataIndex: 'nbTransactions', align: 'right' },
    { title: 'Total', dataIndex: 'ctrlSum', align: 'right', render: formatMontant },
    {
      title: 'XSD',
      render: (_, f) => (f.valideXsd ? <Tag color="green">Conforme</Tag> : <Tag color="red">{f.nbErreurs} erreur(s)</Tag>),
    },
    {
      title: 'IA',
      render: (_, f) => (f.nbSuspectsIa == null
        ? <Tag>Non analysé</Tag>
        : f.nbSuspectsIa > 0 ? <Tag color="orange">{f.nbSuspectsIa} suspect(s)</Tag> : <Tag color="green">Aucun suspect</Tag>),
    },
    {
      title: 'Statut',
      dataIndex: 'statutFichier',
      render: (s) => {
        const statut = STATUTS_FICHIER[s] || { label: s, color: 'default' }
        return <Tag color={statut.color}>{statut.label}</Tag>
      },
    },
    { title: 'Par', dataIndex: 'generePar', render: (p) => p || '—' },
    {
      title: 'Actions',
      fixed: 'right',
      render: (_, f) => (
        // Les boutons ne doivent pas déclencher l'ouverture de la ligne.
        <Space onClick={(e) => e.stopPropagation()}>
          <Button size="small" onClick={() => navigate(urlResultat(f.id))}>Voir le résultat</Button>
          <ActionButton onClick={() => telechargerFichier(f.id)} disabled={!f.valideXsd} succes="Fichier téléchargé">
            Retélécharger
          </ActionButton>
        </Space>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Historique des fichiers"
        subtitle="Cliquez sur une ligne pour ouvrir le résultat du contrôle."
        actions={[<Link key="nouveau" to={BASE}><Button type="primary">Nouveau virement</Button></Link>]}
      />
      {erreur && <Alert type="error" showIcon message={erreur} style={{ margin: '16px 0' }} />}
      <Space wrap style={{ margin: '16px 0' }}>
        <Input.Search
          allowClear
          placeholder="Rechercher un fichier, une société, une période…"
          style={{ width: 360, maxWidth: '100%' }}
          onChange={(e) => setRecherche(e.target.value)}
        />
        <Select
          allowClear
          placeholder="Tous les statuts"
          style={{ width: 200 }}
          value={statut}
          onChange={setStatut}
          options={Object.entries(STATUTS_FICHIER).map(([value, { label }]) => ({ value, label }))}
        />
      </Space>
      <Table
        rowKey="id"
        columns={colonnes}
        dataSource={lignes}
        loading={chargement}
        pagination={{ pageSize: 15, hideOnSinglePage: true }}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: 'Aucun fichier SEPA enregistré dans l’historique.' }}
        onRow={(f) => ({ onClick: () => navigate(urlResultat(f.id)), style: { cursor: 'pointer' } })}
      />
    </div>
  )
}
