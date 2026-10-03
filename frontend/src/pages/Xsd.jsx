import { useEffect, useState } from 'react'
import api from '../api/client'

export default function Xsd() {
  const [schemas, setSchemas] = useState([])
  const [file, setFile] = useState(null)

  const load = () => api.get('/xsd').then(({ data }) => setSchemas(data))

  useEffect(() => { load() }, [])

  const importer = async (e) => {
    e.preventDefault()
    if (!file) return
    const formData = new FormData()
    formData.append('file', file)
    await api.post('/xsd/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    setFile(null)
    load()
  }

  const supprimer = async (id, nom) => {
    if (window.confirm(`Êtes-vous sûr de vouloir supprimer le schéma "${nom}" ?`)) {
      await api.delete(`/xsd/${id}`)
      load()
    }
  }

  return (
    <div>
      <h2>Schémas XSD</h2>

      <form className="inline-form" onSubmit={importer}>
        <input type="file" accept=".xsd,.xml" onChange={(e) => setFile(e.target.files[0])} />
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
              <td><button onClick={() => supprimer(s.id, s.nom)}>Supprimer</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
