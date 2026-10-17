import { useEffect, useRef, useState } from 'react'
import api from '../api/client'
import WebcamCapture from '../components/WebcamCapture.jsx'
import { useAuth } from '../context/AuthContext.jsx'

const apiError = (err, fallback) => err?.response?.data?.erreur || fallback

export default function Profil() {
  const { user } = useAuth()
  const webcamRef = useRef(null)
  const [enregistre, setEnregistre] = useState(null)
  const [camera, setCamera] = useState(false)
  const [cameraReady, setCameraReady] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const [info, setInfo] = useState('')

  useEffect(() => {
    api.get('/face/me').then(({ data }) => setEnregistre(data.enregistre)).catch(() => setEnregistre(false))
  }, [])

  const ouvrirCamera = () => {
    setError('')
    setInfo('')
    setCameraReady(false)
    setCamera(true)
  }

  const enregistrer = async () => {
    const image = webcamRef.current?.capture()
    if (!image) return
    setBusy(true)
    setError('')
    try {
      await api.put('/face/me', { image })
      setEnregistre(true)
      setCamera(false)
      setInfo('Visage enregistré. Vous pouvez maintenant vous connecter par reconnaissance faciale.')
    } catch (err) {
      setError(apiError(err, "Échec de l'enregistrement du visage"))
    } finally {
      setBusy(false)
    }
  }

  const supprimer = async () => {
    setBusy(true)
    setError('')
    try {
      await api.delete('/face/me')
      setEnregistre(false)
      setInfo('Visage supprimé. La connexion faciale est désactivée pour votre compte.')
    } catch (err) {
      setError(apiError(err, 'Échec de la suppression'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <div>
      <h2>Mon profil</h2>
      <p>{user?.username} — {user?.role}</p>

      <h3>Reconnaissance faciale</h3>
      {enregistre === null ? (
        <p className="muted">Chargement…</p>
      ) : (
        <p>Statut : <strong>{enregistre ? 'visage enregistré' : 'aucun visage enregistré'}</strong></p>
      )}
      {info && <div className="info">{info}</div>}
      {error && <div className="error">{error}</div>}

      {camera ? (
        <div className="face-enroll">
          <p className="muted">Placez votre visage bien éclairé, seul et de face, puis capturez.</p>
          <WebcamCapture ref={webcamRef} onReady={() => setCameraReady(true)} />
          <div className="actions">
            <button onClick={enregistrer} disabled={busy || !cameraReady}>
              {busy ? 'Analyse…' : 'Capturer et enregistrer'}
            </button>
            <button className="secondary" onClick={() => setCamera(false)} disabled={busy}>Annuler</button>
          </div>
        </div>
      ) : (
        <div className="actions">
          <button onClick={ouvrirCamera} disabled={busy || enregistre === null}>
            {enregistre ? 'Remplacer mon visage' : 'Enregistrer mon visage'}
          </button>
          {enregistre && (
            <button className="secondary" onClick={supprimer} disabled={busy}>Supprimer mon visage</button>
          )}
        </div>
      )}
    </div>
  )
}
