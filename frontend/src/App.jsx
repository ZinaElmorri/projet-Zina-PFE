import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import { useAuth } from './context/AuthContext.jsx'
import Audit from './pages/Audit.jsx'
import Anomalies from './pages/Anomalies.jsx'
import Dashboard from './pages/Dashboard.jsx'
import KpiDashboard from './pages/KpiDashboard.jsx'
import Login from './pages/Login.jsx'
import Profil from './pages/Profil.jsx'
import Register from './pages/Register.jsx'
import Transactions from './pages/Transactions.jsx'
import VirementsMasse from './pages/VirementsMasse.jsx'
import Users from './pages/Users.jsx'
import Xsd from './pages/Xsd.jsx'

export default function App() {
  const { loading } = useAuth()
  if (loading) return <p style={{ padding: 24 }}>Chargement…</p>

  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/register" element={<Register />} />
      <Route
        element={
          <ProtectedRoute>
            <Layout />
          </ProtectedRoute>
        }
      >
        <Route path="/" element={<Dashboard />} />
        <Route path="/kpi" element={<KpiDashboard />} />
        <Route path="/transactions" element={<Transactions />} />
        <Route path="/anomalies" element={<Anomalies />} />
        <Route path="/virements-masse" element={<VirementsMasse />} />
        <Route path="/xsd" element={<Xsd />} />
        <Route path="/audit" element={<Audit />} />
        <Route path="/users" element={<Users />} />
        <Route path="/profil" element={<Profil />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
