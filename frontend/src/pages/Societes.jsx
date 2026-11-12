import { useEffect, useState } from 'react'
import { Button, Form, Input, Modal, Space, Switch, Table, Tag, message } from 'antd'
import api from '../api/client'
import { messageErreur } from '../utils/erreurs.js'

const VALEURS_INITIALES = { code: '', nom: '', iban: '', bic: '', actif: true }

export default function Societes() {
  const [societes, setSocietes] = useState([])
  const [chargement, setChargement] = useState(false)
  // null = fermée ; {} = création ; société existante = modification
  const [edition, setEdition] = useState(null)
  const [enregistrement, setEnregistrement] = useState(false)
  const [form] = Form.useForm()

  const charger = async () => {
    setChargement(true)
    try {
      const { data } = await api.get('/societes')
      setSocietes(data)
    } catch (e) {
      message.error(`Erreur : ${messageErreur(e)}`)
    } finally {
      setChargement(false)
    }
  }

  useEffect(() => { charger() }, [])

  const ouvrir = (societe) => {
    setEdition(societe ?? {})
    form.setFieldsValue(societe
      ? { code: societe.code, nom: societe.nom, iban: '', bic: societe.bic, actif: societe.actif }
      : VALEURS_INITIALES)
  }

  const enregistrer = async (valeurs) => {
    setEnregistrement(true)
    try {
      if (edition.id) {
        await api.put(`/societes/${edition.id}`, valeurs)
      } else {
        await api.post('/societes', valeurs)
      }
      message.success('Société enregistrée')
      setEdition(null)
      charger()
    } catch (e) {
      message.error(`Erreur : ${messageErreur(e)}`)
    } finally {
      setEnregistrement(false)
    }
  }

  const modification = !!edition?.id

  const colonnes = [
    { title: 'Code', dataIndex: 'code' },
    { title: 'Nom', dataIndex: 'nom' },
    { title: 'IBAN', dataIndex: 'ibanMasque' },
    { title: 'BIC', dataIndex: 'bic' },
    {
      title: 'Statut',
      dataIndex: 'actif',
      render: (actif) => (actif ? <Tag color="green">Active</Tag> : <Tag>Inactive</Tag>),
    },
    {
      title: 'Actions',
      render: (_, societe) => <Button size="small" onClick={() => ouvrir(societe)}>Modifier</Button>,
    },
  ]

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <h2 style={{ margin: 0 }}>Sociétés débitrices</h2>
        <Button type="primary" onClick={() => ouvrir()}>Nouvelle société</Button>
      </Space>
      <p>Les données débiteur sont reprises automatiquement lors de la génération des virements de masse. L'IBAN complet n'est jamais affiché.</p>

      <Table rowKey="id" columns={colonnes} dataSource={societes} loading={chargement} pagination={false} />

      <Modal
        title={modification ? `Modifier ${edition.code}` : 'Nouvelle société'}
        open={edition !== null}
        onCancel={() => setEdition(null)}
        onOk={() => form.submit()}
        okText="Enregistrer"
        cancelText="Annuler"
        confirmLoading={enregistrement}
        destroyOnClose
      >
        <Form form={form} layout="vertical" onFinish={enregistrer} initialValues={VALEURS_INITIALES}>
          <Form.Item name="code" label="Code (identifiant utilisé par les transactions)" rules={[{ required: true, message: 'Le code est obligatoire.' }]}>
            <Input disabled={modification} />
          </Form.Item>
          <Form.Item name="nom" label="Nom de la société" rules={[{ required: true, message: 'Le nom est obligatoire.' }]}>
            <Input />
          </Form.Item>
          <Form.Item
            name="iban"
            label="IBAN"
            extra={modification ? "Laissez vide pour conserver l'IBAN actuel." : undefined}
            rules={[{ required: !modification, message: "L'IBAN est obligatoire." }]}
          >
            <Input placeholder={modification ? edition.ibanMasque : 'FR76 3000 6000 0112 3456 7890 189'} />
          </Form.Item>
          <Form.Item name="bic" label="BIC" rules={[{ required: true, message: 'Le BIC est obligatoire.' }]}>
            <Input />
          </Form.Item>
          {modification && (
            <Form.Item name="actif" label="Société active" valuePropName="checked">
              <Switch />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  )
}
