import { useState } from 'react'

function Noeud({ el, recherche }) {
  const [ouvert, setOuvert] = useState(true)
  const aEnfants = el.enfants && el.enfants.length > 0

  const correspond = (n) => {
    if (!recherche) return true
    const r = recherche.toLowerCase()
    if ((n.nom || '').toLowerCase().includes(r) || (n.chemin || '').toLowerCase().includes(r)) return true
    return (n.enfants || []).some(correspond)
  }
  if (!correspond(el)) return null

  return (
    <div className="xsd-noeud">
      <div className="xsd-ligne">
        {aEnfants ? (
          <button className="xsd-toggle" onClick={() => setOuvert(!ouvert)}>{ouvert ? '▾' : '▸'}</button>
        ) : <span className="xsd-toggle" />}
        <span className="xsd-nom">{el.nom}</span>
        {el.obligatoire
          ? <span className="xsd-badge xsd-badge--req">Obligatoire</span>
          : <span className="xsd-badge xsd-badge--opt">Optionnel</span>}
        {el.repetable && <span className="xsd-badge xsd-badge--rep">Répétable</span>}
        {el.type && <span className="xsd-type">{el.type}</span>}
        {el.maxLength != null && <span className="xsd-contrainte">max {el.maxLength} car.</span>}
        {el.minLength != null && <span className="xsd-contrainte">min {el.minLength} car.</span>}
        {el.fractionDigits != null && <span className="xsd-contrainte">{el.fractionDigits} décimales</span>}
        {el.pattern && <span className="xsd-contrainte" title={el.pattern}>format</span>}
        {el.valeursAutorisees && (
          <span className="xsd-contrainte" title={el.valeursAutorisees.join(', ')}>
            {el.valeursAutorisees.length} valeur(s)
          </span>
        )}
      </div>
      {aEnfants && ouvert && (
        <div className="xsd-enfants">
          {el.enfants.map((enfant, i) => <Noeud el={enfant} recherche={recherche} key={enfant.chemin + i} />)}
        </div>
      )}
    </div>
  )
}

export default function XsdAnalyse({ analyse }) {
  const [recherche, setRecherche] = useState('')
  if (!analyse) return <p className="muted">Sélectionnez un XSD pour voir son analyse.</p>

  return (
    <div className="xsd-analyse">
      <div className="xsd-resume">
        <span><strong>Version :</strong> {analyse.version}</span>
        <span><strong>Namespace :</strong> {analyse.namespace}</span>
        <span><strong>Éléments :</strong> {analyse.nbElements}</span>
        <span><strong>Obligatoires :</strong> {analyse.nbObligatoires}</span>
      </div>
      <input className="xsd-recherche" placeholder="Rechercher un élément…"
        value={recherche} onChange={(e) => setRecherche(e.target.value)} />
      <div className="xsd-arbre">
        <Noeud el={analyse.racine} recherche={recherche} />
      </div>
    </div>
  )
}
