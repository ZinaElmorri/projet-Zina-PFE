import axios from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_URL || 'http://localhost:8080/api',
})

// Injecte le token JWT dans chaque requête.
api.interceptors.request.use((config) => {
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
  (response) => response,
  (error) => {
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
