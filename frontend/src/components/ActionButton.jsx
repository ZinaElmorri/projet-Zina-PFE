import { useState } from 'react'
import { App } from 'antd'
import Spinner from './Spinner.jsx'
import { messageErreur } from '../utils/erreurs.js'

/**
 * Bouton d'action : spinner et désactivation pendant l'appel, confirmation optionnelle,
 * toast de succès ou d'erreur.
 *
 *   <ActionButton onClick={supprimer} succes="Transaction supprimée"
 *                 confirmation={{ titre: 'Supprimer ?', danger: true }}>Supprimer</ActionButton>
 */
export default function ActionButton({
  onClick,
  succes,
  confirmation,
  enCours = false,
  type = 'button',
  className,
  disabled,
  children,
}) {
  const [occupe, setOccupe] = useState(false)
  const { message, modal } = App.useApp()
  const chargement = enCours || occupe

  const executer = async () => {
    setOccupe(true)
    try {
      await onClick()
      if (succes) message.success(succes)
    } catch (e) {
      message.error(`Erreur : ${messageErreur(e)}`)
    } finally {
      setOccupe(false)
    }
  }

  const demarrer = () => {
    if (!confirmation) return executer()
    modal.confirm({
      title: confirmation.titre,
      content: confirmation.contenu,
      okText: confirmation.libelle || 'Confirmer',
      cancelText: 'Annuler',
      okButtonProps: { danger: !!confirmation.danger },
      onOk: executer,
    })
  }

  return (
    <button
      type={type}
      className={className}
      disabled={disabled || chargement}
      onClick={onClick ? demarrer : undefined}
    >
      {chargement && <Spinner taille="sm" />}
      {children}
    </button>
  )
}
