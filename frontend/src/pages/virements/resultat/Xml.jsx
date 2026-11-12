import { App, Button, Empty, Space, Typography } from 'antd'
import { useOutletContext, useSearchParams } from 'react-router-dom'
import ActionButton from '../../../components/ActionButton.jsx'
import XmlViewer from '../../../components/XmlViewer.jsx'
import { telechargerFichier } from '../parcours.js'

export default function Xml() {
  const { resultat } = useOutletContext()
  const [params] = useSearchParams()
  const { message } = App.useApp()
  const ligne = Number(params.get('ligne')) || null
  const erreurs = [...resultat.erreursXsd, ...resultat.metier.erreursXml]

  const copier = async () => {
    await navigator.clipboard.writeText(resultat.xml)
    message.success('XML copié dans le presse-papiers')
  }

  if (!resultat.xml) return <Empty description="Aucun XML enregistré pour ce fichier." />

  return (
    <div>
      <Space wrap style={{ marginBottom: 8 }}>
        <Button onClick={copier}>Copier</Button>
        <ActionButton onClick={() => telechargerFichier(resultat.resume.id)} disabled={!resultat.resume.valideXsd} succes="Fichier téléchargé">
          Télécharger
        </ActionButton>
        <Typography.Text type="secondary">
          Lignes rouges : erreurs ; lignes orange : avertissements.
          {!resultat.resume.valideXsd && ' Téléchargement indisponible : le XML n’est pas conforme.'}
        </Typography.Text>
      </Space>
      <XmlViewer
        xml={resultat.xml}
        erreurs={erreurs}
        avertissements={resultat.metier.avertissementsXml}
        ligneActive={ligne}
      />
    </div>
  )
}
