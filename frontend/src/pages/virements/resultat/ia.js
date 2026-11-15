// Niveau de suspicion d'une fiche : mêmes bornes que le backend (0,35 et 0,6).
export const NIVEAUX = {
  ELEVE: { label: 'Élevé', color: 'red', ordre: 3 },
  MOYEN: { label: 'Moyen', color: 'orange', ordre: 2 },
  FAIBLE: { label: 'Faible', color: 'green', ordre: 1 },
}

/** Niveau fourni par le backend ; calculé à partir du score pour les fichiers antérieurs à ce champ. */
export const niveauDe = (ligne) => {
  if (ligne.niveau) return ligne.niveau
  if (ligne.score == null) return null
  return ligne.score >= 0.6 ? 'ELEVE' : ligne.score >= 0.35 ? 'MOYEN' : 'FAIBLE'
}

export const TYPE_NON_PRECISE = 'Type non précisé'

/** [{ type, nombre }] trié par nombre décroissant, pour les fiches suspectes. */
export const repartitionParType = (lignes) => {
  const compte = {}
  lignes.filter((l) => l.suspect).forEach((l) => {
    const type = l.typeProbable || TYPE_NON_PRECISE
    compte[type] = (compte[type] || 0) + 1
  })
  return Object.entries(compte).map(([type, nombre]) => ({ type, nombre })).sort((a, b) => b.nombre - a.nombre)
}
