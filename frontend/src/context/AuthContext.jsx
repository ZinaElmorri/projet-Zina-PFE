import { createContext, useContext, useEffect, useState } from 'react'
import api from '../api/client'

const AuthContext = createContext(null)

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const token = localStorage.getItem('token')
    const username = localStorage.getItem('username')
    const role = localStorage.getItem('role')
    if (token && username) {
      setUser({ username, role })
    }
    setLoading(false)
  }, [])

  const storeSession = (data) => {
    localStorage.setItem('token', data.token)
    localStorage.setItem('username', data.username)
    localStorage.setItem('role', data.role)
    setUser({ username: data.username, role: data.role })
  }

  // Retourne { otpRequis, challengeId, emailMasque } si un code OTP doit être saisi.
  const login = async (username, password) => {
    const { data } = await api.post('/auth/login', { username, password })
    if (!data.otpRequis) storeSession(data)
    return data
  }

  const faceLogin = async (username, image) => {
    const { data } = await api.post('/auth/face-login', { username, image })
    if (!data.otpRequis) storeSession(data)
    return data
  }

  const verifyOtp = async (challengeId, code) => {
    const { data } = await api.post('/auth/verify-otp', { challengeId, code })
    storeSession(data)
    return data
  }

  const resendOtp = async (challengeId) => {
    const { data } = await api.post('/auth/resend-otp', { challengeId })
    return data
  }

  const logout = () => {
    localStorage.clear()
    setUser(null)
  }

  return (
    <AuthContext.Provider value={{ user, login, faceLogin, verifyOtp, resendOtp, logout, loading }}>
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  return useContext(AuthContext)
}
