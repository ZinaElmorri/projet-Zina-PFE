import { Alert, Table, Tag } from 'antd'
import { useOutletContext } from 'react-router-dom'

export default function ControlesMetier() {
  const { resultat } = useOutletContext()
  const { erreurs, avertissements } = resultat.metier
  const lignes = [
    ...erreurs.map((l) => ({ ...l, gravite: 'erreur' })),
    ...avertissements.map((l) => ({ ...l, gravite: 'avertissement' })),
  ]
    .sort((a, b) => String(a.matricule ?? '').localeCompare(String(b.matricule ?? '')))
    .map((l, index) => ({ ...l, cle: index }))

  const colonnes = [
    {
      title: 'Gravité',
      dataIndex: 'gravite',
      width: 130,
      render: (g) => (g === 'erreur' ? <Tag color="red">Erreur</Tag> : <Tag color="orange">Avertissement</Tag>),
      filters: [{ text: 'Erreur', value: 'erreur' }, { text: 'Avertissement', value: 'avertissement' }],
      onFilter: (valeur, l) => l.gravite === valeur,
    },
    { title: 'Matricule', dataIndex: 'matricule', width: 120, render: (m) => m || '—' },
    { title: 'Nom', dataIndex: 'nom', width: 200, render: (n) => n || '—' },
    { title: 'Message', dataIndex: 'message' },
  ]

  return (
    <div>
      {erreurs.length === 0
        ? <Alert type="success" showIcon style={{ marginBottom: 12 }} message="Aucune erreur de contrôle métier." />
        : <Alert type="error" showIcon style={{ marginBottom: 12 }} message={`${erreurs.length} erreur(s) de contrôle métier : corrigez les paiements concernés puis relancez le contrôle.`} />}
      <Table
        rowKey="cle"
        size="small"
        columns={colonnes}
        dataSource={lignes}
        pagination={{ pageSize: 15, hideOnSinglePage: true }}
        locale={{ emptyText: 'Aucune erreur ni avertissement métier.' }}
      />
    </div>
  )
}
