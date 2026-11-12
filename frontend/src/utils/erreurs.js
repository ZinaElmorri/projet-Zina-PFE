export function messageErreur(e) {
  return e?.response?.data?.erreur
    || e?.response?.data?.message
    || e?.message
    || 'Une erreur est survenue'
}
