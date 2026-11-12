import api from '../../api/client'

export const BASE = '/virements-masse'
export const urlResultat = (id, sousPage = 'synthese') => `${BASE}/resultat/${id}/${sousPage}`

export const STATUTS_FICHIER = {
  BROUILLON: { label: 'Brouillon', color: 'blue' },
  NON_CONFORME: { label: 'Non conforme', color: 'red' },
}

export const messageEchec = (erreur) => {
  if (erreur.response?.status === 401) return 'Votre session a expiré. Reconnectez-vous pour continuer.'
  if (erreur.response?.status === 403) return "Votre compte n'a pas les droits nécessaires pour cette opération. Contactez un administrateur."
  if (erreur.response?.status >= 500) return 'Le serveur a rencontré un problème. Réessayez plus tard ; si le problème persiste, contactez un administrateur.'
  if (typeof erreur.response?.data?.erreur === 'string') return erreur.response.data.erreur
  if (erreur.isAxiosError) return erreur.response
    ? 'La demande a été refusée. Vérifiez les paramètres et les données sélectionnées, puis réessayez.'
    : 'Le serveur est injoignable. Vérifiez votre connexion et la disponibilité du service, puis réessayez.'
  return erreur.message || "L'opération a échoué. Réessayez ; si le problème persiste, contactez un administrateur."
}

// Aujourd'hui + 2 jours ouvrés.
export const dateExecutionDefaut = () => {
  const d = new Date()
  let ajoutes = 0
  while (ajoutes < 2) {
    d.setDate(d.getDate() + 1)
    const j = d.getDay()
    if (j !== 0 && j !== 6) ajoutes++
  }
  return d.toISOString().slice(0, 10)
}

// La préparation est gardée le temps de la session pour « Retour à la préparation ».
const CLE_PREPARATION = 'sepa.preparationVirement'

export const sauverPreparation = (preparation) => sessionStorage.setItem(CLE_PREPARATION, JSON.stringify(preparation))

export const lirePreparation = () => {
  try {
    return JSON.parse(sessionStorage.getItem(CLE_PREPARATION))
  } catch {
    return null
  }
}

/** Télécharge le XML d'un fichier enregistré ; lève une erreur lisible si le serveur refuse. */
export async function telechargerFichier(id) {
  let resp
  try {
    resp = await api.get(`/masse/fichiers/${id}/telecharger`, { responseType: 'blob', libelleChargement: 'Téléchargement du fichier…' })
  } catch (e) {
    if (e.response?.data instanceof Blob) {
      let erreur
      try { erreur = JSON.parse(await e.response.data.text()).erreur } catch { erreur = null }
      if (erreur) throw new Error(erreur)
    }
    throw new Error(messageEchec(e))
  }
  const cd = resp.headers['content-disposition'] || ''
  const m = cd.match(/filename="?([^"]+)"?/)
  const nom = m ? m[1] : `SEPA_${id}.xml`
  const url = window.URL.createObjectURL(new Blob([resp.data], { type: 'application/xml' }))
  const a = document.createElement('a')
  a.href = url
  a.download = nom
  a.click()
  window.URL.revokeObjectURL(url)
}
