import { useEffect, useState, useSyncExternalStore } from 'react'
import Spinner from './Spinner.jsx'
import { loadingStore } from '../loading/loadingStore.js'

// Une requête plus rapide que ce délai n'affiche pas le voile (pas de clignotement).
const DELAI_AFFICHAGE_MS = 300

export default function GlobalLoader() {
  const nombre = useSyncExternalStore(loadingStore.abonner, loadingStore.getNombre)
  const libelle = useSyncExternalStore(loadingStore.abonner, loadingStore.getLibelle)
  const actif = nombre > 0
  const [visible, setVisible] = useState(false)

  useEffect(() => {
    if (!actif) {
      setVisible(false)
      return undefined
    }
    const delai = setTimeout(() => setVisible(true), DELAI_AFFICHAGE_MS)
    return () => clearTimeout(delai)
  }, [actif])

  if (!visible) return null
  return (
    <div className="sepa-voile" aria-busy="true">
      <Spinner taille="lg" texte={libelle || 'Chargement…'} />
    </div>
  )
}
