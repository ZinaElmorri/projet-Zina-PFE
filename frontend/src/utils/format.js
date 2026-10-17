import dayjs from 'dayjs'

const eur = new Intl.NumberFormat('fr-FR', { style: 'currency', currency: 'EUR' })
const deuxDecimales = new Intl.NumberFormat('fr-FR', { minimumFractionDigits: 2, maximumFractionDigits: 2 })

const vide = (v) => v == null || v === ''

/** 2450 -> "2 450,00 €" */
export const formatMontant = (v) => (vide(v) ? '—' : eur.format(Number(v)))

/** -> "17/10/2026 14:32" */
export const formatDate = (v) => (vide(v) ? '—' : dayjs(v).format('DD/MM/YYYY HH:mm'))

/** -> "17/10/2026" */
export const formatDateCourte = (v) => (vide(v) ? '—' : dayjs(v).format('DD/MM/YYYY'))

/** Valeur déjà exprimée en pourcentage : 0.18 -> "0,18 %" */
export const formatPourcentage = (v) => (vide(v) ? '—' : `${deuxDecimales.format(Number(v))}\u00a0%`)

/** "FR7630006000011234567890189" -> "FR76 •••• •••• 0189" */
export const masquerIban = (iban) => {
  if (vide(iban)) return '—'
  const c = String(iban).replace(/\s+/g, '').toUpperCase()
  return c.length < 8 ? c : `${c.slice(0, 4)} •••• •••• ${c.slice(-4)}`
}
