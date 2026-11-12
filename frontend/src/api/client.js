import axios from 'axios'
import { loadingStore } from '../loading/loadingStore.js'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api',
})

// Injecte le token JWT dans chaque requête.
// Une requête passe par le voile de chargement sauf si config.silent est vrai.
api.interceptors.request.use((config) => {
  if (!config.silent) {
    config.comptee = true
    loadingStore.demarrer(config.libelleChargement)
  }
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // Désactive le cache navigateur sur les GET (données toujours fraîches).
  if ((config.method || 'get').toLowerCase() === 'get') {
    config.headers['Cache-Control'] = 'no-cache'
    config.headers['Pragma'] = 'no-cache'
    config.params = { ...(config.params || {}), _t: Date.now() }
  }
  return config
})

// Redirige vers la connexion en cas de 401.
api.interceptors.response.use(
  (response) => {
    if (response.config?.comptee) loadingStore.terminer()
    return response
  },
  (error) => {
    if (error.config?.comptee) loadingStore.terminer()
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default api
