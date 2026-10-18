import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Spin } from 'antd'
import Layout from './components/Layout.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import { useAuth } from './context/AuthContext.jsx'

const Audit = lazy(() => import('./pages/Audit.jsx'))
const Anomalies = lazy(() => import('./pages/Anomalies.jsx'))
const Dashboard = lazy(() => import('./pages/Dashboard.jsx'))
const KpiDashboard = lazy(() => import('./pages/KpiDashboard.jsx'))
const Login = lazy(() => import('./pages/Login.jsx'))
const Profil = lazy(() => import('./pages/Profil.jsx'))
const Register = lazy(() => import('./pages/Register.jsx'))
const Transactions = lazy(() => import('./pages/Transactions.jsx'))
const VirementsMasse = lazy(() => import('./pages/VirementsMasse.jsx'))
const Users = lazy(() => import('./pages/Users.jsx'))
const Xsd = lazy(() => import('./pages/Xsd.jsx'))

const chargement = <div className="app-loading"><Spin /></div>

export default function App() {
  const { loading } = useAuth()
  if (loading) return chargement

  return (
    <Suspense fallback={chargement}>
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
    </Suspense>
  )
}
