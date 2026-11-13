import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert, Button, Table, Tag } from 'antd'
import api from '../../api/client'
import PageHeader from '../../components/PageHeader.jsx'
import { formatDate, formatMontant } from '../../utils/format.js'
import { messageEchec, urlResultat } from './parcours.js'

/** Liste du Manager : fichiers soumis par les Experts RH et en attente de décision. */
export default function FichiersAValider() {
  const navigate = useNavigate()
  const [fichiers, setFichiers] = useState([])
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState('')

  useEffect(() => {
    api.get('/masse/fichiers', { params: { statut: 'SOUMIS' }, silent: true })
      .then(({ data }) => setFichiers(data))
      .catch((e) => setErreur(messageEchec(e)))
      .finally(() => setChargement(false))
  }, [])

  const colonnes = [
    { title: 'Généré le', dataIndex: 'genereLe', render: formatDate, sorter: (a, b) => String(a.genereLe).localeCompare(String(b.genereLe)), defaultSortOrder: 'ascend' },
    { title: 'Société', dataIndex: 'clientId', render: (c) => c || '—' },
    { title: 'Période', dataIndex: 'periode', render: (p) => p || '—' },
    { title: 'Salariés', dataIndex: 'nbTransactions', align: 'right' },
    { title: 'Total', dataIndex: 'ctrlSum', align: 'right', render: formatMontant },
    { title: 'XSD', render: () => <Tag color="green">Conforme</Tag> },
    {
      title: 'IA',
      render: (_, f) => (f.nbSuspectsIa == null
        ? <Tag>Non analysé</Tag>
        : f.nbSuspectsIa > 0 ? <Tag color="orange">{f.nbSuspectsIa} suspect(s)</Tag> : <Tag color="green">Aucun suspect</Tag>),
    },
    { title: 'Soumis par', dataIndex: 'soumisPar', render: (p) => p || '—' },
    {
      title: 'Actions',
      fixed: 'right',
      render: (_, f) => <Button type="primary" size="small" onClick={() => navigate(urlResultat(f.id))}>Examiner</Button>,
    },
  ]

  return (
    <div>
      <PageHeader
        title="Fichiers à valider"
        subtitle="Ouvrez un fichier pour consulter son résultat, puis validez-le ou rejetez-le avec un motif."
      />
      {erreur && <Alert type="error" showIcon message={erreur} style={{ margin: '16px 0' }} />}
      <Table
        rowKey="id"
        style={{ marginTop: 16 }}
        columns={colonnes}
        dataSource={fichiers}
        loading={chargement}
        pagination={{ pageSize: 15, hideOnSinglePage: true }}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: 'Aucun fichier en attente de validation.' }}
        onRow={(f) => ({ onClick: () => navigate(urlResultat(f.id)), style: { cursor: 'pointer' } })}
      />
    </div>
  )
}
