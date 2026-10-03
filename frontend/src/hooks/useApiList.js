import { useCallback, useEffect, useState } from 'react'
import api from '../api/client'

/**
 * Charge une liste depuis l'API au montage et expose reload() pour la rafraîchir.
 * Les réponses ne sont jamais mises en cache (voir NoCacheApiFilter côté backend
 * et l'intercepteur axios côté frontend).
 *
 *   const { data, loading, error, reload, setData } = useApiList('/xsd')
 */
export function useApiList(url, params) {
  const [data, setData] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    setLoading(true)
    setError('')
    try {
      const { data } = await api.get(url, params ? { params } : undefined)
      setData(data)
    } catch (e) {
      setError(e.response?.data?.message || e.message)
    } finally {
      setLoading(false)
    }
  }, [url, JSON.stringify(params)]) // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { reload() }, [reload])

  return { data, loading, error, reload, setData }
}

/**
 * Exécute une action (await), puis recharge la liste.
 * Renvoie un message de succès/erreur prêt à afficher.
 *
 *   const msg = await runAndReload(() => api.delete('/xsd/' + id), reload, 'Schéma supprimé')
 */
export async function runAndReload(action, reload, messageSucces = '') {
  try {
    await action()
    if (reload) await reload()
    return { ok: true, message: messageSucces }
  } catch (e) {
    return { ok: false, message: e.response?.data?.message || e.message }
  }
}
