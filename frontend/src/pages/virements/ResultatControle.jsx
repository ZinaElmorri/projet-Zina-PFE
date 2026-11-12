import { useEffect, useState } from 'react'
import { Link, Outlet, useLocation, useNavigate, useParams } from 'react-router-dom'
import { Button, Result, Skeleton, Tabs, Tag } from 'antd'
import api from '../../api/client'
import PageHeader from '../../components/PageHeader.jsx'
import ParcoursEtapes from '../../components/ParcoursEtapes.jsx'
import { BASE, messageEchec, STATUTS_FICHIER, urlResultat } from './parcours.js'
import '../virements-masse.css'

const SOUS_PAGES = ['synthese', 'xml', 'validation-xsd', 'controle-ia', 'controles-metier']

const compteur = (n, color) => (n > 0 ? <Tag color={color} style={{ marginInlineStart: 6, marginInlineEnd: 0 }}>{n}</Tag> : null)

/** Page « Résultat du contrôle » : charge le fichier une fois et expose les sous-pages via des onglets routés. */
export default function ResultatControle() {
  const { id } = useParams()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const [resultat, setResultat] = useState(null)
  const [erreur, setErreur] = useState('')

  useEffect(() => {
    let actif = true
    setResultat(null)
    setErreur('')
    api.get(`/masse/fichiers/${id}/resultat`, { silent: true })
      .then(({ data }) => actif && setResultat(data))
      .catch((e) => actif && setErreur(messageEchec(e)))
    return () => { actif = false }
  }, [id])

  if (erreur) {
    return (
      <Result
        status="warning"
        title="Résultat indisponible"
        subTitle={erreur}
        extra={<Link to={`${BASE}/historique`}><Button type="primary">Retour à l’historique</Button></Link>}
      />
    )
  }
  if (!resultat) return <Skeleton active paragraph={{ rows: 8 }} />

  const sousPage = pathname.split('/').pop()
  const actif = SOUS_PAGES.includes(sousPage) ? sousPage : 'synthese'
  const statut = STATUTS_FICHIER[resultat.resume.statutFichier] || { label: resultat.resume.statutFichier, color: 'default' }
  const nbErreursXsd = resultat.erreursXsd.length
  const nbErreursMetier = resultat.metier.erreurs.length

  const onglets = [
    { key: 'synthese', label: 'Synthèse' },
    { key: 'xml', label: 'XML' },
    { key: 'validation-xsd', label: <span>Validation XSD{compteur(nbErreursXsd, 'red')}</span> },
    { key: 'controle-ia', label: <span>Contrôle IA{compteur(resultat.ia.nbSuspects, 'orange')}</span> },
    { key: 'controles-metier', label: <span>Contrôles métier{compteur(nbErreursMetier, 'red')}</span> },
  ]

  return (
    <div>
      <PageHeader
        title="Résultat du contrôle"
        subtitle={resultat.resume.nomFichier}
        actions={[
          <Tag key="statut" color={statut.color}>{statut.label}</Tag>,
          <Link key="historique" to={`${BASE}/historique`}><Button>Historique des fichiers</Button></Link>,
        ]}
      />
      <ParcoursEtapes etape={resultat.resume.valideXsd ? 2 : 1} />
      <Tabs activeKey={actif} items={onglets} onChange={(cle) => navigate(urlResultat(id, cle))} />
      <Outlet context={{ resultat, id }} />
    </div>
  )
}
