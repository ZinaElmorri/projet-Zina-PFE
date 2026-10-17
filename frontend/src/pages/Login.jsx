import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import WebcamCapture from '../components/WebcamCapture.jsx'
import { useAuth } from '../context/AuthContext.jsx'

const apiError = (err, fallback) => err?.response?.data?.erreur || fallback

export default function Login() {
  const { login, faceLogin, verifyOtp, resendOtp } = useAuth()
  const navigate = useNavigate()
  const webcamRef = useRef(null)
  const [faceMode, setFaceMode] = useState(false)
  const [cameraReady, setCameraReady] = useState(false)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')
  const [challenge, setChallenge] = useState(null)
  const [code, setCode] = useState('')
  const [busy, setBusy] = useState(false)

  const handleSubmit = async (e) => {
    e.preventDefault()
    setError('')
    let image
    if (faceMode) {
      image = webcamRef.current?.capture()
      if (!image) {
        setError('Caméra non prête, réessayez dans un instant')
        return
      }
    }
    setBusy(true)
    try {
      const data = faceMode ? await faceLogin(username, image) : await login(username, password)
      if (data.otpRequis) {
        setChallenge({ id: data.challengeId, emailMasque: data.emailMasque })
        setInfo(`Un code de vérification à 6 chiffres a été envoyé à ${data.emailMasque}.`)
        setPassword('')
        setFaceMode(false)
      } else {
        navigate('/')
      }
    } catch (err) {
      setError(apiError(err, faceMode ? 'Visage non reconnu' : 'Identifiants invalides'))
    } finally {
      setBusy(false)
    }
  }

  const toggleFaceMode = () => {
    setError('')
    setCameraReady(false)
    setFaceMode(!faceMode)
  }

  const handleVerify = async (e) => {
    e.preventDefault()
    setError('')
    setBusy(true)
    try {
      await verifyOtp(challenge.id, code)
      navigate('/')
    } catch (err) {
      const message = apiError(err, 'Code invalide')
      setError(message)
      setCode('')
      if (/reconnecter/i.test(message)) resetOtp(false)
    } finally {
      setBusy(false)
    }
  }

  const handleResend = async () => {
    setError('')
    setBusy(true)
    try {
      const data = await resendOtp(challenge.id)
      setInfo(`Un nouveau code a été envoyé à ${data.emailMasque}.`)
    } catch (err) {
      setError(apiError(err, "Impossible de renvoyer le code"))
    } finally {
      setBusy(false)
    }
  }

  const resetOtp = (clearError = true) => {
    setChallenge(null)
    setCode('')
    setInfo('')
    if (clearError) setError('')
  }

  if (challenge) {
    return (
      <div className="login-page">
        <form className="login-card" onSubmit={handleVerify}>
          <h1>Plateforme SEPA</h1>
          <p className="subtitle">Vérification de l'email</p>
          {info && <div className="info">{info}</div>}
          {error && <div className="error">{error}</div>}
          <label>
            Code de vérification
            <input
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, '').slice(0, 6))}
              inputMode="numeric"
              autoComplete="one-time-code"
              pattern="\d{6}"
              maxLength={6}
              placeholder="123456"
              autoFocus
              required
            />
          </label>
          <button type="submit" disabled={busy || code.length !== 6}>Vérifier</button>
          <button type="button" className="secondary" onClick={handleResend} disabled={busy}>
            Renvoyer le code
          </button>
          <button type="button" className="link-btn" onClick={() => resetOtp()}>
            Retour à la connexion
          </button>
        </form>
      </div>
    )
  }

  return (
    <div className="login-page">
      <form className="login-card" onSubmit={handleSubmit}>
        <h1>Plateforme SEPA</h1>
        <p className="subtitle">{faceMode ? 'Connexion par reconnaissance faciale' : 'Connexion'}</p>
        {error && <div className="error">{error}</div>}
        <label>
          Utilisateur
          <input value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        {faceMode ? (
          <WebcamCapture ref={webcamRef} onReady={() => setCameraReady(true)} />
        ) : (
          <label>
            Mot de passe
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
          </label>
        )}
        <button type="submit" disabled={busy || (faceMode && !cameraReady)}>
          {faceMode ? (busy ? 'Analyse du visage…' : 'Capturer et se connecter') : 'Se connecter'}
        </button>
        <button type="button" className="secondary" onClick={toggleFaceMode} disabled={busy}>
          {faceMode ? 'Connexion par mot de passe' : 'Connexion par reconnaissance faciale'}
        </button>
        <Link to="/register">Créer un compte</Link>
      </form>
    </div>
  )
}
