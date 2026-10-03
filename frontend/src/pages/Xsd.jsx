import { useEffect, useRef, useState } from 'react'
import api from '../api/client'

export default function Xsd() {
  const [schemas, setSchemas] = useState([])
  const [file, setFile] = useState(null)
  const [editId, setEditId] = useState(null)
  const [editFile, setEditFile] = useState(null)
  const [suppressionId, setSuppressionId] = useState(null)
  const [erreur, setErreur] = useState('')
  const [succes, setSucces] = useState('')
  const fileRef = useRef(null)

  const load = () => api.get('/xsd').then(({ data }) => setSchemas(data))

  useEffect(() => { load() }, [])

  // Efface le message de succès au bout de 3 secondes.
  useEffect(() => {
    if (!succes) return
    const t = setTimeout(() => setSucces(''), 3000)
    return () => clearTimeout(t)
  }, [succes])

  const importer = async (e) => {
    e.preventDefault()
    if (!file) return
    setErreur('')
    const formData = new FormData()
    formData.append('file', file)
    try {
      await api.post('/xsd/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setFile(null)
      if (fileRef.current) fileRef.current.value = ''
      await load()
      setSucces('Schéma importé')
    } catch (e) {
      setErreur('Import impossible : ' + (e.response?.data?.message || e.message))
    }
  }

  const editer = async (id) => {
    if (!editFile) return
    setErreur('')
    const formData = new FormData()
    formData.append('file', editFile)
    try {
      await api.put(`/xsd/${id}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      setEditId(null)
      setEditFile(null)
      await load()
      setSucces('Schéma mis à jour')
    } catch (e) {
      setErreur('Mise à jour impossible : ' + (e.response?.data?.message || e.message))
    }
  }

  const supprimer = async (id, nom) => {
    if (!window.confirm(`Êtes-vous sûr de vouloir supprimer le schéma "${nom}" ?`)) return
    setErreur('')
    setSuppressionId(id)
    try {
      await api.delete(`/xsd/${id}`)
      setSchemas((prev) => prev.filter((s) => s.id !== id))
      await load()
      setSucces('Schéma supprimé')
    } catch (e) {
      setErreur('Suppression impossible : ' + (e.response?.data?.message || e.message))
    } finally {
      setSuppressionId(null)
    }
  }

  return (
    <div>
      <h2>Schémas XSD</h2>

      {erreur && <div className="error">{erreur}</div>}
      {succes && <div className="info">{succes}</div>}

      <form className="inline-form" onSubmit={importer}>
        <input ref={fileRef} type="file" accept=".xsd,.xml" onChange={(e) => setFile(e.target.files[0])} />
        <button type="submit">Importer</button>
      </form>

      <table>
        <thead>
          <tr><th>ID</th><th>Nom</th><th>Importé par</th><th>Actions</th></tr>
        </thead>
        <tbody>
          {schemas.map((s) => (
            <tr key={s.id}>
              <td>{s.id}</td>
              <td>{s.nom}</td>
              <td>{s.importePar}</td>
              <td>
                {editId === s.id ? (
                  <>
                    <input
                      type="file"
                      accept=".xsd,.xml"
                      onChange={(e) => setEditFile(e.target.files[0])}
                    />
                    <button onClick={() => editer(s.id)}>Enregistrer</button>
                    <button onClick={() => { setEditId(null); setEditFile(null) }}>Annuler</button>
                  </>
                ) : (
                  <>
                    <button onClick={() => { setEditId(s.id); setEditFile(null) }}>Éditer</button>
                    <button
                      onClick={() => supprimer(s.id, s.nom)}
                      disabled={suppressionId === s.id}
                    >
                      {suppressionId === s.id ? 'Suppression…' : 'Supprimer'}
                    </button>
                  </>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
