import { Alert, Table, Tag } from 'antd'
import { useNavigate, useOutletContext } from 'react-router-dom'
import { urlResultat } from '../parcours.js'

export default function ValidationXsd() {
  const { resultat, id } = useOutletContext()
  const navigate = useNavigate()
  const lignes = [
    ...resultat.erreursXsd.map((e) => ({ ...e, gravite: 'erreur' })),
    ...resultat.avertissementsXsd.map((a) => ({ ...a, gravite: 'avertissement' })),
  ].map((l, index) => ({ ...l, cle: index }))

  const colonnes = [
    {
      title: 'Gravité',
      dataIndex: 'gravite',
      width: 130,
      render: (g) => (g === 'erreur' ? <Tag color="red">Erreur</Tag> : <Tag color="orange">Avertissement</Tag>),
      filters: [{ text: 'Erreur', value: 'erreur' }, { text: 'Avertissement', value: 'avertissement' }],
      onFilter: (valeur, l) => l.gravite === valeur,
    },
    { title: 'Ligne', dataIndex: 'ligne', width: 90, render: (n) => n || '—' },
    { title: 'Balise', dataIndex: 'element', width: 200, render: (e) => e || '—' },
    { title: 'Message', dataIndex: 'message' },
  ]

  return (
    <div>
      {resultat.resume.valideXsd
        ? <Alert type="success" showIcon style={{ marginBottom: 12 }}
            message={`Conforme au schéma XSD${resultat.resume.versionMessage ? ` (${resultat.resume.versionMessage})` : ''}.`} />
        : <Alert type="error" showIcon style={{ marginBottom: 12 }}
            message={`XML non conforme : ${resultat.erreursXsd.length} erreur(s) à corriger avant le téléchargement.`} />}
      <Table
        rowKey="cle"
        size="small"
        columns={colonnes}
        dataSource={lignes}
        pagination={{ pageSize: 15, hideOnSinglePage: true }}
        locale={{ emptyText: 'Aucune erreur ni avertissement de validation XSD.' }}
        rowClassName={(l) => (l.gravite === 'erreur' ? 'masse-erreur-ligne' : 'masse-erreur-ligne masse-erreur-ligne--warn')}
        onRow={(l) => ({
          onClick: () => l.ligne > 0 && navigate(`${urlResultat(id, 'xml')}?ligne=${l.ligne}`),
          style: { cursor: l.ligne > 0 ? 'pointer' : 'default' },
        })}
      />
      {lignes.some((l) => l.ligne > 0) && (
        <p className="muted" style={{ marginTop: 8 }}>Cliquez sur une ligne pour l’afficher dans le XML.</p>
      )}
    </div>
  )
}
