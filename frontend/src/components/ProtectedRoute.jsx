import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import AccesRefuse from '../pages/AccesRefuse.jsx'

/** `autorise` (facultatif) reçoit le rôle de l'utilisateur ; s'il renvoie faux, la page « Accès refusé » s'affiche. */
export default function ProtectedRoute({ children, autorise }) {
  const { user } = useAuth()
  if (!user) {
    return <Navigate to="/login" replace />
  }
  if (autorise && !autorise(user.role)) {
    return <AccesRefuse />
  }
  return children
}
