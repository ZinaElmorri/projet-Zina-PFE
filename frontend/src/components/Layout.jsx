import { Link, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

export default function Layout() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <div className="app">
      <aside className="sidebar">
        <h1 className="logo">SEPA</h1>
        <nav>
          <Link to="/">Tableau de bord</Link>
          <Link to="/kpi">Indicateurs KPI</Link>
          <Link to="/transactions">Transactions</Link>
          <Link to="/anomalies">Anomalies</Link>
          <Link to="/xsd">Schémas XSD</Link>
          <Link to="/audit">Journal d'audit</Link>
          {user?.role === 'ADMINISTRATEUR' && <Link to="/users">Utilisateurs</Link>}
        </nav>
      </aside>
      <div className="main">
        <header className="topbar">
          <span>{user?.username} — {user?.role}</span>
          <button onClick={handleLogout}>Déconnexion</button>
        </header>
        <main className="content">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
