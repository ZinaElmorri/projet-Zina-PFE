import './Spinner.css'

const BARRES = 12

export default function Spinner({ taille = 'md', texte, className = '' }) {
  return (
    <span className={`sepa-spinner sepa-spinner--${taille} ${className}`.trim()} role="status" aria-label={texte || 'Chargement'}>
      <span className="sepa-spinner__cercle">
        {Array.from({ length: BARRES }, (_, i) => (
          <span key={i} className="sepa-spinner__barre" style={{ '--i': i }} />
        ))}
      </span>
      {texte && <span className="sepa-spinner__texte">{texte}</span>}
    </span>
  )
}
