import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import api from '../api/client'
import WebcamCapture from '../components/WebcamCapture.jsx'

export const ROLES = [
  { value: 'ADMINISTRATEUR', label: 'Administrateur' },
  { value: 'GESTIONNAIRE_RH', label: 'Gestionnaire RH' },
  { value: 'VALIDATEUR', label: 'Validateur' },
  { value: 'AUDITEUR', label: 'Auditeur' },
]

const empty = { username: '', nomComplet: '', email: '', role: 'GESTIONNAIRE_RH', password: '', confirmation: '' }

export default function Register() {
  const webcamRef = useRef(null)
  const [form, setForm] = useState(empty)
  const [photo, setPhoto] = useState(null)
  const [cameraReady, setCameraReady] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [succes, setSucces] = useState('')

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const prendrePhoto = () => {
    const image = webcamRef.current?.capture()
    if (image) setPhoto(image)
  }

  const reprendre = () => {
    setCameraReady(false)
    setPhoto(null)
  }

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    if (form.password !== form.confirmation) {
      setError('Les mots de passe ne correspondent pas')
      return
    }
    if (!photo) {
      setError('Prenez une photo de votre visage avant de valider')
      return
    }
    setBusy(true)
    try {
      const { confirmation, ...payload } = form
      const { data } = await api.post('/auth/register', { ...payload, image: photo })
      setSucces(data.message)
    } catch (err) {
      setError(err?.response?.data?.erreur || 'Échec de la création du compte')
      if (/visage/i.test(err?.response?.data?.erreur || '')) reprendre()
    } finally {
      setBusy(false)
    }
  }

  if (succes) {
    return (
      <div className="login-page">
        <div className="login-card">
          <h1>Plateforme SEPA</h1>
          <p className="subtitle">Demande envoyée</p>
          <div className="info">{succes}</div>
          <Link to="/login">Retour à la connexion</Link>
        </div>
      </div>
    )
  }

  return (
    <div className="login-page">
      <form className="login-card login-card--wide" onSubmit={handleSubmit}>
        <h1>Plateforme SEPA</h1>
        <p className="subtitle">Créer un compte</p>
        {error && <div className="error">{error}</div>}
        <label>
          Nom complet
          <input name="nomComplet" value={form.nomComplet} onChange={handleChange} required />
        </label>
        <label>
          Nom d'utilisateur
          <input name="username" value={form.username} onChange={handleChange} minLength={3} maxLength={50}
                 pattern="[A-Za-z0-9._\-]+" title="Lettres, chiffres, '.', '_' et '-'" required />
        </label>
        <label>
          Email
          <input name="email" type="email" value={form.email} onChange={handleChange} required />
        </label>
        <label>
          Type d'utilisateur
          <select name="role" value={form.role} onChange={handleChange} required>
            {ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
          </select>
        </label>
        <label>
          Mot de passe
          <input name="password" type="password" value={form.password} onChange={handleChange} minLength={8} required />
        </label>
        <label>
          Confirmer le mot de passe
          <input name="confirmation" type="password" value={form.confirmation} onChange={handleChange} minLength={8} required />
        </label>

        <div className="face-capture">
          <span>Photo du visage (reconnaissance faciale)</span>
          {photo ? (
            <>
              <img src={photo} alt="Visage capturé" className="webcam" />
              <button type="button" className="secondary" onClick={reprendre} disabled={busy}>Reprendre la photo</button>
            </>
          ) : (
            <>
              <WebcamCapture ref={webcamRef} onReady={() => setCameraReady(true)} />
              <button type="button" className="secondary" onClick={prendrePhoto} disabled={!cameraReady}>
                Prendre la photo
              </button>
            </>
          )}
        </div>

        <button type="submit" disabled={busy || !photo}>{busy ? 'Création…' : 'Créer mon compte'}</button>
        <Link to="/login">J'ai déjà un compte</Link>
      </form>
    </div>
  )
}
