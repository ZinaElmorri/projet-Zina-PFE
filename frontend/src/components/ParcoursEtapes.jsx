import { Steps } from 'antd'

const ETAPES = [
  { title: 'Préparation' },
  { title: 'Contrôle' },
  { title: 'Validation / Téléchargement' },
]

/** Indicateur d'étapes du parcours de virement de masse (0 = préparation, 1 = contrôle, 2 = téléchargement). */
export default function ParcoursEtapes({ etape }) {
  return <Steps size="small" current={etape} items={ETAPES} style={{ margin: '16px 0 24px' }} />
}
