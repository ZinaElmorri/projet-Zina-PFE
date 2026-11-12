import { Button, Card, Col, Descriptions, Row, Space, Tag, Typography } from 'antd'
import { useNavigate, useOutletContext } from 'react-router-dom'
import ActionButton from '../../../components/ActionButton.jsx'
import { formatDateCourte, formatMontant } from '../../../utils/format.js'
import { BASE, STATUTS_FICHIER, telechargerFichier, urlResultat } from '../parcours.js'

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
  const { resultat, id } = useOutletContext()
  const navigate = useNavigate()
  const { resume, metier, ia } = resultat
  const nbErreursXsd = resultat.erreursXsd.length
  const nbAvertissementsXsd = resultat.avertissementsXsd.length
  const nbErreursMetier = metier.erreurs.length
  const statut = STATUTS_FICHIER[resume.statutFichier] || { label: resume.statutFichier, color: 'default' }

  const vers = (sousPage) => () => navigate(urlResultat(id, sousPage))

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
            detail={resume.valideXsd ? 'Le XML peut être téléchargé.' : 'Corrigez les erreurs puis régénérez le fichier.'}
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

      <Space wrap>
        <ActionButton
          onClick={() => telechargerFichier(resume.id)}
          disabled={!resume.valideXsd}
          succes="Fichier téléchargé"
        >
          Télécharger le XML
        </ActionButton>
        <Button onClick={() => navigate(BASE, { state: { restaurer: true } })}>Retour à la préparation</Button>
      </Space>
      {!resume.valideXsd && (
        <div style={{ marginTop: 8 }}>
          <Typography.Text type="secondary">Le téléchargement est bloqué tant que le XML n’est pas conforme au schéma XSD.</Typography.Text>
        </div>
      )}
    </div>
  )
}
