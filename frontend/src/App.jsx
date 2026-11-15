import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { Spin } from 'antd'
import Layout from './components/Layout.jsx'
import GlobalLoader from './components/GlobalLoader.jsx'
import ProtectedRoute from './components/ProtectedRoute.jsx'
import { useAuth } from './context/AuthContext.jsx'
import {
  PAGE_ACCUEIL_ADMIN,
  peutAffecter,
  peutConsulterAudit,
  peutGererSocietes,
  peutGererUtilisateurs,
  peutPreparer,
  peutTraiterAnomalies,
  peutValider,
  peutVoirDashboard,
  peutVoirKpi,
  peutVoirQualiteIa,
  peutVoirResultat,
  peutVoirTransactions,
} from './constants/roles.js'

const Affectations = lazy(() => import('./pages/Affectations.jsx'))
const FichiersAValider = lazy(() => import('./pages/virements/FichiersAValider.jsx'))
const Audit = lazy(() => import('./pages/Audit.jsx'))
const Anomalies = lazy(() => import('./pages/Anomalies.jsx'))
const Dashboard = lazy(() => import('./pages/Dashboard.jsx'))
const KpiDashboard = lazy(() => import('./pages/KpiDashboard.jsx'))
const Login = lazy(() => import('./pages/Login.jsx'))
const Profil = lazy(() => import('./pages/Profil.jsx'))
const QualiteModeleIa = lazy(() => import('./pages/QualiteModeleIa.jsx'))
const Register = lazy(() => import('./pages/Register.jsx'))
const Transactions = lazy(() => import('./pages/Transactions.jsx'))
const VirementsHistorique = lazy(() => import('./pages/virements/Historique.jsx'))
const NouveauVirement = lazy(() => import('./pages/virements/NouveauVirement.jsx'))
const ResultatControle = lazy(() => import('./pages/virements/ResultatControle.jsx'))
const ResultatSynthese = lazy(() => import('./pages/virements/resultat/Synthese.jsx'))
const ResultatXml = lazy(() => import('./pages/virements/resultat/Xml.jsx'))
const ResultatValidationXsd = lazy(() => import('./pages/virements/resultat/ValidationXsd.jsx'))
const ResultatControleIa = lazy(() => import('./pages/virements/resultat/ControleIa.jsx'))
const ResultatControlesMetier = lazy(() => import('./pages/virements/resultat/ControlesMetier.jsx'))
const Societes = lazy(() => import('./pages/Societes.jsx'))
const Users = lazy(() => import('./pages/Users.jsx'))
const Xsd = lazy(() => import('./pages/Xsd.jsx'))

const chargement = <div className="app-loading"><Spin /></div>

export default function App() {
  const { loading } = useAuth()
  if (loading) return chargement

  return (
    <Suspense fallback={chargement}>
      <GlobalLoader />
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
          <Route path="/" element={<ProtectedRoute autorise={peutVoirDashboard} redirection={PAGE_ACCUEIL_ADMIN}><Dashboard /></ProtectedRoute>} />
          <Route path="/kpi" element={<ProtectedRoute autorise={peutVoirKpi}><KpiDashboard /></ProtectedRoute>} />
          <Route path="/transactions" element={<ProtectedRoute autorise={peutVoirTransactions}><Transactions /></ProtectedRoute>} />
          <Route path="/anomalies" element={<ProtectedRoute autorise={peutTraiterAnomalies}><Anomalies /></ProtectedRoute>} />
          <Route path="/virements-masse">
            <Route index element={<ProtectedRoute autorise={peutPreparer}><NouveauVirement /></ProtectedRoute>} />
            <Route path="historique" element={<VirementsHistorique />} />
            <Route path="a-valider" element={<ProtectedRoute autorise={peutValider}><FichiersAValider /></ProtectedRoute>} />
            <Route path="resultat/:id" element={<ProtectedRoute autorise={peutVoirResultat}><ResultatControle /></ProtectedRoute>}>
              <Route index element={<Navigate to="synthese" replace />} />
              <Route path="synthese" element={<ResultatSynthese />} />
              <Route path="xml" element={<ResultatXml />} />
              <Route path="validation-xsd" element={<ResultatValidationXsd />} />
              <Route path="controle-ia" element={<ResultatControleIa />} />
              <Route path="controles-metier" element={<ResultatControlesMetier />} />
            </Route>
          </Route>
          <Route path="/affectations" element={<ProtectedRoute autorise={peutAffecter}><Affectations /></ProtectedRoute>} />
          <Route path="/societes" element={<ProtectedRoute autorise={peutGererSocietes}><Societes /></ProtectedRoute>} />
          <Route path="/xsd" element={<Xsd />} />
          <Route path="/qualite-ia" element={<ProtectedRoute autorise={peutVoirQualiteIa}><QualiteModeleIa /></ProtectedRoute>} />
          <Route path="/audit" element={<ProtectedRoute autorise={peutConsulterAudit}><Audit /></ProtectedRoute>} />
          <Route path="/users" element={<ProtectedRoute autorise={peutGererUtilisateurs}><Users /></ProtectedRoute>} />
          <Route path="/profil" element={<Profil />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
