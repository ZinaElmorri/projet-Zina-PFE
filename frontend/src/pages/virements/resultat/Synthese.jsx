import { useState } from 'react'
import { Alert, App, Button, Card, Col, Descriptions, Empty, Input, Modal, Row, Space, Table, Tag, Typography } from 'antd'
import { useNavigate, useOutletContext } from 'react-router-dom'
import api from '../../../api/client'
import ActionButton from '../../../components/ActionButton.jsx'
import XmlViewer from '../../../components/XmlViewer.jsx'
import { peutPreparer, peutSoumettre, peutValider } from '../../../constants/roles.js'
import { useAuth } from '../../../context/AuthContext.jsx'
import { formatDate, formatDateCourte, formatMontant } from '../../../utils/format.js'
import { BASE, messageEchec, STATUTS_FICHIER, telechargerFichier, urlResultat } from '../parcours.js'

function CarteControle({ titre, couleur, valeur, detail, onClick }) {
  return (
    <Card hoverable onClick={onClick} style={{ height: '100%', borderTop: `3px solid ${couleur}` }}>
      <Typography.Text type="secondary">{titre}</Typography.Text>
      <div style={{ fontSize: 20, fontWeight: 600, color: couleur, margin: '4px 0' }}>{valeur}</div>
      {detail && <Typography.Text type="secondary">{detail}</Typography.Text>}
    </Card>
  )
}

const pluriel = (n, singulier, plurielMot) => `${n} ${n > 1 ? plurielMot : singulier}`

export default function Synthese() {
  const { resultat, id, recharger } = useOutletContext()
  const { user } = useAuth()
  const { message } = App.useApp()
  const navigate = useNavigate()
  const [motifOuvert, setMotifOuvert] = useState(false)
  const [motif, setMotif] = useState('')
  const [rejetEnCours, setRejetEnCours] = useState(false)
  const [ligneActive, setLigneActive] = useState(null)

  // Rouge : erreurs XSD et erreurs métier positionnées ; orange : avertissements (XSD omis et métier).
  const erreursXml = [...resultat.erreursXsd, ...resultat.metier.erreursXml]
  const avertissementsXml = [...resultat.avertissementsXsd, ...resultat.metier.avertissementsXml]
    .filter((a, i, tous) => tous.findIndex((b) => b.ligne === a.ligne && b.message === a.message) === i)
  const diagnostics = [
    ...erreursXml.map((e) => ({ ...e, gravite: 'erreur' })),
    ...avertissementsXml.map((a) => ({ ...a, gravite: 'avertissement' })),
  ].map((d, i) => ({ ...d, cle: i }))

  const copierXml = async () => {
    await navigator.clipboard.writeText(resultat.xml)
    message.success('XML copié dans le presse-papiers')
  }
  const { resume, metier, ia } = resultat
  const nbErreursXsd = resultat.erreursXsd.length
  const nbAvertissementsXsd = resultat.avertissementsXsd.length
  const nbErreursMetier = metier.erreurs.length
  const statutCode = resume.statutFichier
  const statut = STATUTS_FICHIER[statutCode] || { label: statutCode, color: 'default' }
  const estValide = statutCode === 'VALIDE'
  const peutSoumettreFichier = peutSoumettre(user?.role) && statutCode === 'BROUILLON' && resume.valideXsd
  const peutDecider = peutValider(user?.role) && statutCode === 'SOUMIS'
  // Quatre yeux : celui qui a généré ou soumis le fichier ne peut pas décider.
  const quatreYeux = !!user?.username && (user.username === resume.generePar || user.username === resume.soumisPar)

  const vers = (sousPage) => () => navigate(urlResultat(id, sousPage))

  const action = (chemin, corps) => async () => {
    try {
      await api.post(`/masse/fichiers/${resume.id}/${chemin}`, corps)
    } catch (e) {
      throw new Error(messageEchec(e))
    }
    await recharger()
  }
  const soumettre = action('soumettre')
  const valider = action('valider')

  const rejeter = async () => {
    if (!motif.trim()) {
      message.warning('Le motif est obligatoire pour rejeter un fichier.')
      return
    }
    setRejetEnCours(true)
    try {
      await api.post(`/masse/fichiers/${resume.id}/rejeter`, { motif: motif.trim() })
      message.success('Fichier rejeté')
      setMotifOuvert(false)
      await recharger()
    } catch (e) {
      message.error(`Erreur : ${messageEchec(e)}`)
    } finally {
      setRejetEnCours(false)
    }
  }

  return (
    <div>
      <Row gutter={[16, 16]} style={{ marginBottom: 16 }}>
        <Col xs={24} md={12} xl={6}>
          <CarteControle
            titre="Validation XSD"
            couleur={resume.valideXsd ? '#389e0d' : '#cf1322'}
            valeur={resume.valideXsd ? 'Conforme' : pluriel(nbErreursXsd, 'erreur', 'erreurs')}
            detail={pluriel(nbAvertissementsXsd, 'avertissement', 'avertissements')}
            onClick={vers('validation-xsd')}
          />
        </Col>
        <Col xs={24} md={12} xl={6}>
          <CarteControle
            titre="Contrôles métier"
            couleur={nbErreursMetier > 0 ? '#cf1322' : '#389e0d'}
            valeur={nbErreursMetier > 0 ? pluriel(nbErreursMetier, 'erreur', 'erreurs') : 'Aucune erreur'}
            detail={pluriel(metier.avertissements.length, 'avertissement', 'avertissements')}
            onClick={vers('controles-metier')}
          />
        </Col>
        <Col xs={24} md={12} xl={6}>
          <CarteControle
            titre="Contrôle IA"
            couleur={!ia.disponible ? '#8c8c8c' : ia.nbSuspects > 0 ? '#d46b08' : '#389e0d'}
            valeur={!ia.disponible ? 'Analyse IA indisponible'
              : ia.nbSuspects > 0 ? pluriel(ia.nbSuspects, 'montant suspect', 'montants suspects') : 'Aucun montant suspect'}
            detail={ia.disponible ? `${pluriel(ia.nbAnalyses, 'paiement analysé', 'paiements analysés')}${ia.modelVersion ? ` — modèle ${ia.modelVersion}` : ''}` : 'Le fichier reste exploitable sans l’IA.'}
            onClick={vers('controle-ia')}
          />
        </Col>
        <Col xs={24} md={12} xl={6}>
          <CarteControle
            titre="Statut du fichier"
            couleur="#1677ff"
            valeur={<Tag color={statut.color} style={{ fontSize: 14, padding: '2px 10px' }}>{statut.label}</Tag>}
            detail={estValide ? 'Validé : le XML peut être envoyé à la banque.'
              : resume.valideXsd ? 'Téléchargement de contrôle possible (non validé).'
                : 'Corrigez les erreurs puis régénérez le fichier.'}
          />
        </Col>
      </Row>

      <Card title="Fichier généré" style={{ marginBottom: 16 }}>
        <Descriptions bordered size="small" column={{ xs: 1, md: 2 }}>
          <Descriptions.Item label="Société">{resume.clientId || '—'}</Descriptions.Item>
          <Descriptions.Item label="Débiteur">{resultat.nomDebiteur || '—'}</Descriptions.Item>
          <Descriptions.Item label="Période de paie">{resume.periode || '—'}</Descriptions.Item>
          <Descriptions.Item label="Date d’exécution">{formatDateCourte(resultat.dateExecution)}</Descriptions.Item>
          <Descriptions.Item label="Nombre de salariés">{resume.nbTransactions}</Descriptions.Item>
          <Descriptions.Item label="Montant total">{formatMontant(resume.ctrlSum)}</Descriptions.Item>
          <Descriptions.Item label="Schéma XSD">{resume.xsdNom}{resume.versionMessage ? ` (${resume.versionMessage})` : ''}</Descriptions.Item>
          <Descriptions.Item label="IBAN débiteur">{resultat.ibanDebiteurMasque || '—'}</Descriptions.Item>
        </Descriptions>
      </Card>

      {statutCode === 'SOUMIS' && (
        <Alert type="warning" showIcon style={{ marginBottom: 16 }}
          message={`En attente de validation par un Manager (soumis par ${resume.soumisPar || '—'}).`} />
      )}
      {statutCode === 'VALIDE' && (
        <Alert type="success" showIcon style={{ marginBottom: 16 }}
          message={`Fichier validé par ${resume.decisionPar || '—'}${resume.decisionLe ? ` le ${formatDate(resume.decisionLe)}` : ''} : il peut être envoyé à la banque.`} />
      )}
      {statutCode === 'REJETE' && (
        <Alert type="error" showIcon style={{ marginBottom: 16 }}
          message={`Fichier rejeté par ${resume.decisionPar || '—'}${resume.decisionLe ? ` le ${formatDate(resume.decisionLe)}` : ''}`}
          description={<span><strong>Motif :</strong> {resume.motifRejet}. Corrigez puis relancez un nouveau contrôle depuis la préparation.</span>} />
      )}

      <Card
        title="Aperçu du XML généré"
        style={{ marginTop: 24 }}
        extra={<Button size="small" onClick={copierXml} disabled={!resultat.xml}>Copier le XML</Button>}
      >
        {!resultat.xml ? <Empty description="Aucun XML enregistré pour ce fichier." /> : (
          <>
            <Space wrap size="middle" style={{ marginBottom: 12 }}>
              <Tag color="red">{erreursXml.length} erreur(s) — lignes rouges</Tag>
              <Tag color="orange">{avertissementsXml.length} avertissement(s) — lignes orange</Tag>
              {diagnostics.length > 0 && <Typography.Text type="secondary">Cliquez sur un message pour aller à la ligne.</Typography.Text>}
            </Space>
            {diagnostics.length === 0 && (
              <Alert type="success" showIcon style={{ marginBottom: 12 }} message="Aucune erreur ni avertissement : le XML est conforme." />
            )}
            {diagnostics.length > 0 && (
              <Table
                size="small"
                rowKey="cle"
                style={{ marginBottom: 12 }}
                pagination={{ pageSize: 5, hideOnSinglePage: true, size: 'small' }}
                dataSource={diagnostics}
                columns={[
                  { title: 'Gravité', dataIndex: 'gravite', width: 130, render: (g) => (g === 'erreur' ? <Tag color="red">Erreur</Tag> : <Tag color="orange">Avertissement</Tag>) },
                  { title: 'Ligne', dataIndex: 'ligne', width: 80, render: (n) => n || '—' },
                  { title: 'Balise', dataIndex: 'element', width: 140, render: (e) => e || '—' },
                  { title: 'Message', dataIndex: 'message' },
                ]}
                onRow={(d) => ({ onClick: () => d.ligne > 0 && setLigneActive(d.ligne), style: { cursor: d.ligne > 0 ? 'pointer' : 'default' } })}
              />
            )}
            <XmlViewer xml={resultat.xml} erreurs={erreursXml} avertissements={avertissementsXml} ligneActive={ligneActive} />
          </>
        )}
      </Card>

      <div style={{ marginTop: 24 }}>
        <Space wrap>
          <ActionButton
            onClick={() => telechargerFichier(resume.id)}
            disabled={!resume.valideXsd}
            succes={estValide ? 'Fichier téléchargé' : 'Fichier de contrôle téléchargé (non validé)'}
          >
            {estValide ? 'Télécharger pour la banque' : 'Télécharger (contrôle — non validé)'}
          </ActionButton>
          {peutSoumettreFichier && (
            <ActionButton
              onClick={soumettre}
              succes="Fichier soumis à validation"
              confirmation={{ titre: 'Soumettre ce fichier à validation ?', contenu: 'Un Manager pourra le valider ou le rejeter. Vous ne pourrez plus le modifier.', libelle: 'Soumettre' }}
            >
              Soumettre à validation
            </ActionButton>
          )}
          {peutDecider && (
            <>
              <ActionButton
                onClick={valider}
                disabled={quatreYeux}
                succes="Fichier validé"
                confirmation={{ titre: 'Valider ce fichier ?', contenu: 'Il pourra ensuite être téléchargé pour envoi à la banque.', libelle: 'Valider' }}
              >
                Valider
              </ActionButton>
              <Button danger disabled={quatreYeux} onClick={() => { setMotif(''); setMotifOuvert(true) }}>Rejeter</Button>
            </>
          )}
          {peutPreparer(user?.role) && (
            <Button onClick={() => navigate(BASE, { state: { restaurer: true } })}>Retour à la préparation</Button>
          )}
        </Space>
        {peutDecider && quatreYeux && (
          <div style={{ marginTop: 8 }}>
            <Typography.Text type="secondary">Principe des quatre yeux : vous ne pouvez pas valider un fichier que vous avez généré ou soumis.</Typography.Text>
          </div>
        )}
        {!resume.valideXsd && (
          <div style={{ marginTop: 8 }}>
            <Typography.Text type="secondary">Le téléchargement est bloqué tant que le XML n’est pas conforme au schéma XSD.</Typography.Text>
          </div>
        )}
      </div>

      <Modal
        title="Rejeter ce fichier"
        open={motifOuvert}
        onCancel={() => setMotifOuvert(false)}
        onOk={rejeter}
        okText="Rejeter"
        okButtonProps={{ danger: true }}
        cancelText="Annuler"
        confirmLoading={rejetEnCours}
        destroyOnClose
      >
        <Typography.Paragraph>Le motif est obligatoire : il sera communiqué à l’Expert RH.</Typography.Paragraph>
        <Input.TextArea rows={4} value={motif} onChange={(e) => setMotif(e.target.value)} placeholder="Ex. : IBAN du salarié EMP002 à vérifier" maxLength={500} showCount />
      </Modal>
    </div>
  )
}
