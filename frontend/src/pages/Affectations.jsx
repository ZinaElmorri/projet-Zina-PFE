import { useEffect, useState } from 'react'
import { Alert, App, Button, Modal, Select, Table, Tag, Typography } from 'antd'
import api from '../api/client'
import ActionButton from '../components/ActionButton.jsx'
import PageHeader from '../components/PageHeader.jsx'
import { formatDate } from '../utils/format.js'
import { messageEchec } from './virements/parcours.js'

export default function Affectations() {
  const { message } = App.useApp()
  const [lignes, setLignes] = useState([])
  const [experts, setExperts] = useState([])
  const [chargement, setChargement] = useState(true)
  const [erreur, setErreur] = useState('')
  const [enCours, setEnCours] = useState(null)
  const [historique, setHistorique] = useState(null)

  const charger = async () => {
    try {
      const [affectations, listeExperts] = await Promise.all([
        api.get('/affectations', { silent: true }),
        api.get('/affectations/experts', { silent: true }),
      ])
      setLignes(affectations.data)
      setExperts(listeExperts.data)
      setErreur('')
    } catch (e) {
      setErreur(messageEchec(e))
    } finally {
      setChargement(false)
    }
  }

  useEffect(() => { charger() }, [])

  const affecter = async (societeCode, expertId) => {
    setEnCours(societeCode)
    try {
      await api.put(`/affectations/${encodeURIComponent(societeCode)}`, { expertId })
      message.success('Affectation enregistrée')
      await charger()
    } catch (e) {
      message.error(`Erreur : ${messageEchec(e)}`)
    } finally {
      setEnCours(null)
    }
  }

  const retirer = async (societeCode) => {
    await api.delete(`/affectations/${encodeURIComponent(societeCode)}`).catch((e) => { throw new Error(messageEchec(e)) })
    await charger()
  }

  const voirHistorique = async (societeCode) => {
    try {
      const { data } = await api.get('/affectations/historique', { params: { societe: societeCode }, silent: true })
      setHistorique({ societeCode, lignes: data })
    } catch (e) {
      message.error(`Erreur : ${messageEchec(e)}`)
    }
  }

  const colonnes = [
    { title: 'Société', dataIndex: 'societeCode', render: (code, l) => <span><strong>{code}</strong> — {l.societeNom}</span> },
    {
      title: 'Expert RH',
      render: (_, l) => (
        <Select
          placeholder="Non affectée"
          style={{ width: 240 }}
          loading={enCours === l.societeCode}
          disabled={enCours === l.societeCode}
          value={l.expertId ?? undefined}
          options={experts.map((e) => ({ value: e.id, label: `${e.nomComplet} (${e.username})` }))}
          onChange={(expertId) => affecter(l.societeCode, expertId)}
        />
      ),
    },
    { title: 'Depuis le', dataIndex: 'depuis', render: (d) => (d ? formatDate(d) : '—') },
    {
      title: 'Actions',
      render: (_, l) => (
        <>
          <Button size="small" onClick={() => voirHistorique(l.societeCode)}>Historique</Button>{' '}
          {l.expertId && (
            <ActionButton
              onClick={() => retirer(l.societeCode)}
              succes="Affectation retirée"
              confirmation={{ titre: `Retirer l’affectation de ${l.societeCode} ?`, contenu: `${l.expertNom || l.expertUsername} n’aura plus accès à cette société.`, libelle: 'Retirer', danger: true }}
            >
              Retirer
            </ActionButton>
          )}
        </>
      ),
    },
  ]

  return (
    <div>
      <PageHeader
        title="Affectation des sociétés"
        subtitle="Un Expert RH ne prépare des virements que pour les sociétés qui lui sont affectées."
      />
      {erreur && <Alert type="error" showIcon message={erreur} style={{ margin: '16px 0' }} />}
      {experts.length === 0 && !chargement && !erreur && (
        <Alert type="warning" showIcon style={{ margin: '16px 0' }} message="Aucun Expert RH actif : demandez à un administrateur d’en créer un." />
      )}
      <Table
        rowKey="societeCode"
        style={{ marginTop: 16 }}
        columns={colonnes}
        dataSource={lignes}
        loading={chargement}
        pagination={false}
        locale={{ emptyText: 'Aucune société enregistrée.' }}
      />
      <Modal
        title={`Historique des affectations — ${historique?.societeCode || ''}`}
        open={historique !== null}
        onCancel={() => setHistorique(null)}
        footer={<Button onClick={() => setHistorique(null)}>Fermer</Button>}
        width={640}
        destroyOnClose
      >
        <Table
          rowKey={(l) => `${l.debutLe}-${l.expertUsername}`}
          size="small"
          pagination={false}
          dataSource={historique?.lignes || []}
          locale={{ emptyText: 'Aucune affectation enregistrée.' }}
          columns={[
            { title: 'Expert', dataIndex: 'expertUsername' },
            { title: 'Du', dataIndex: 'debutLe', render: formatDate },
            { title: 'Au', dataIndex: 'finLe', render: (d) => (d ? formatDate(d) : <Tag color="green">En cours</Tag>) },
            { title: 'Par', dataIndex: 'affectePar', render: (p) => p || '—' },
          ]}
        />
        <Typography.Paragraph type="secondary" style={{ marginTop: 8 }}>
          Les affectations terminées sont conservées pour la traçabilité.
        </Typography.Paragraph>
      </Modal>
    </div>
  )
}
