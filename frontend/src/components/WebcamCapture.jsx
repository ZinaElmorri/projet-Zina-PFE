import { forwardRef, useEffect, useImperativeHandle, useRef, useState } from 'react'

// Flux caméra + capture JPEG (data URL) exposée via ref.capture().
const WebcamCapture = forwardRef(function WebcamCapture({ onReady }, ref) {
  const videoRef = useRef(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let stream
    let cancelled = false
    navigator.mediaDevices?.getUserMedia({ video: { width: 640, height: 480, facingMode: 'user' }, audio: false })
      .then((s) => {
        if (cancelled) { s.getTracks().forEach((t) => t.stop()); return }
        stream = s
        videoRef.current.srcObject = s
      })
      .catch(() => setError("Impossible d'accéder à la caméra (autorisation refusée ou caméra absente)"))
    if (!navigator.mediaDevices) setError("La caméra n'est pas disponible dans ce navigateur")
    return () => {
      cancelled = true
      stream?.getTracks().forEach((t) => t.stop())
    }
  }, [])

  useImperativeHandle(ref, () => ({
    capture() {
      const video = videoRef.current
      if (!video || !video.videoWidth) return null
      const canvas = document.createElement('canvas')
      canvas.width = video.videoWidth
      canvas.height = video.videoHeight
      canvas.getContext('2d').drawImage(video, 0, 0)
      return canvas.toDataURL('image/jpeg', 0.9)
    },
  }), [])

  if (error) return <div className="error">{error}</div>

  return (
    <video
      ref={videoRef}
      className="webcam"
      autoPlay
      playsInline
      muted
      onLoadedData={() => onReady?.()}
    />
  )
})

export default WebcamCapture
