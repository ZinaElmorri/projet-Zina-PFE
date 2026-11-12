// Compte les requêtes HTTP en cours pour afficher le voile de chargement global.
let nombre = 0
let libelle = ''
const abonnes = new Set()

const notifier = () => abonnes.forEach((cb) => cb())

export const loadingStore = {
  demarrer(nouveauLibelle) {
    nombre += 1
    if (nouveauLibelle) libelle = nouveauLibelle
    notifier()
  },
  terminer() {
    nombre = Math.max(0, nombre - 1)
    if (nombre === 0) libelle = ''
    notifier()
  },
  abonner(cb) {
    abonnes.add(cb)
    return () => abonnes.delete(cb)
  },
  getNombre: () => nombre,
  getLibelle: () => libelle,
}
