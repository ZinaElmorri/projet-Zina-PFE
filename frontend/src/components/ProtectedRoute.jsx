import { Navigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'
import AccesRefuse from '../pages/AccesRefuse.jsx'

/** `autorise` (facultatif) reçoit le rôle de l'utilisateur ; s'il renvoie faux : redirection vers `redirection` si fournie, sinon page « Accès refusé ». */
export default function ProtectedRoute({ children, autorise, redirection }) {
  const { user } = useAuth()
  if (!user) {
    return <Navigate to="/login" replace />
  }
  if (autorise && !autorise(user.role)) {
    return redirection ? <Navigate to={redirection} replace /> : <AccesRefuse />
  }
  return children
}
