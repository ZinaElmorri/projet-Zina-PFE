import { useEffect, useState } from 'react'
import api from '../api/client'
import { ROLES } from './Register.jsx'

const empty = { username: '', password: '', nomComplet: '', email: '', role: 'GESTIONNAIRE_RH' }
const roleLabel = (value) => ROLES.find((r) => r.value === value)?.label || value

const statut = (u) => {
  if (!u.approuve) return { label: 'En attente d\'approbation', cls: 'badge-attente' }
  return u.actif ? { label: 'Actif', cls: 'badge-actif' } : { label: 'Désactivé', cls: 'badge-inactif' }
}

export default function Users() {
  const [users, setUsers] = useState([])
  const [form, setForm] = useState(empty)
  const [error, setError] = useState('')

  const load = () => api.get('/users').then(({ data }) => setUsers(data))

  useEffect(() => { load() }, [])

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const action = async (fn) => {
    setError('')
    try {
      await fn()
      load()
    } catch (err) {
      setError(err?.response?.data?.erreur || 'Action impossible')
    }
  }

  const create = (e) => {
    e.preventDefault()
    action(async () => { await api.post('/users', form); setForm(empty) })
  }

  const deactivate = (id) => action(() => api.delete(`/users/${id}`))
  const approuver = (id) => action(() => api.post(`/users/${id}/approuver`))
  const rejeter = (u) => {
    if (window.confirm(`Rejeter et supprimer la demande de « ${u.username} » ?`)) {
      action(() => api.post(`/users/${u.id}/rejeter`))
    }
  }

  const enAttente = users.filter((u) => !u.approuve).length

  return (
    <div>
      <h2>Gestion des utilisateurs</h2>
      {enAttente > 0 && <div className="info">{enAttente} demande(s) de création de compte en attente d'approbation.</div>}
      {error && <div className="error">{error}</div>}

      <form className="inline-form" onSubmit={create}>
        <input name="username" placeholder="Utilisateur" value={form.username} onChange={handleChange} required />
        <input name="password" type="password" placeholder="Mot de passe" value={form.password} onChange={handleChange} required />
        <input name="nomComplet" placeholder="Nom complet" value={form.nomComplet} onChange={handleChange} required />
        <input name="email" type="email" placeholder="Email" value={form.email} onChange={handleChange} />
        <select name="role" value={form.role} onChange={handleChange}>
          {ROLES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
        </select>
        <button type="submit">Créer</button>
      </form>

      <table>
        <thead>
          <tr><th>ID</th><th>Utilisateur</th><th>Nom</th><th>Email</th><th>Rôle</th><th>Statut</th><th>Actions</th></tr>
        </thead>
        <tbody>
          {users.map((u) => {
            const s = statut(u)
            return (
              <tr key={u.id}>
                <td>{u.id}</td>
                <td>{u.username}</td>
                <td>{u.nomComplet}</td>
                <td>{u.email}</td>
                <td>{roleLabel(u.role)}</td>
                <td><span className={`badge-statut ${s.cls}`}>{s.label}</span></td>
                <td>
                  {!u.approuve && (
                    <>
                      <button onClick={() => approuver(u.id)}>Approuver</button>
                      <button className="secondary" onClick={() => rejeter(u)}>Rejeter</button>
                    </>
                  )}
                  {u.approuve && u.actif && <button onClick={() => deactivate(u.id)}>Désactiver</button>}
                  {u.approuve && !u.actif && <button onClick={() => approuver(u.id)}>Activer</button>}
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
    </div>
  )
}
