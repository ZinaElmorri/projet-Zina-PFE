import { useEffect, useState } from 'react'
import api from '../api/client'

const empty = { username: '', password: '', nomComplet: '', email: '', role: 'GESTIONNAIRE_RH' }
const roles = ['ADMINISTRATEUR', 'GESTIONNAIRE_RH', 'VALIDATEUR', 'AUDITEUR']

export default function Users() {
  const [users, setUsers] = useState([])
  const [form, setForm] = useState(empty)

  const load = () => api.get('/users').then(({ data }) => setUsers(data))

  useEffect(() => { load() }, [])

  const handleChange = (e) => setForm({ ...form, [e.target.name]: e.target.value })

  const create = async (e) => {
    e.preventDefault()
    await api.post('/users', form)
    setForm(empty)
    load()
  }

  const deactivate = async (id) => { await api.delete(`/users/${id}`); load() }

  return (
    <div>
      <h2>Gestion des utilisateurs</h2>

      <form className="inline-form" onSubmit={create}>
        <input name="username" placeholder="Utilisateur" value={form.username} onChange={handleChange} required />
        <input name="password" type="password" placeholder="Mot de passe" value={form.password} onChange={handleChange} required />
        <input name="nomComplet" placeholder="Nom complet" value={form.nomComplet} onChange={handleChange} required />
        <input name="email" type="email" placeholder="Email" value={form.email} onChange={handleChange} />
        <select name="role" value={form.role} onChange={handleChange}>
          {roles.map((r) => <option key={r} value={r}>{r}</option>)}
        </select>
        <button type="submit">Créer</button>
      </form>

      <table>
        <thead>
          <tr><th>ID</th><th>Utilisateur</th><th>Nom</th><th>Rôle</th><th>Actif</th><th>Actions</th></tr>
        </thead>
        <tbody>
          {users.map((u) => (
            <tr key={u.id}>
              <td>{u.id}</td>
              <td>{u.username}</td>
              <td>{u.nomComplet}</td>
              <td>{u.role}</td>
              <td>{u.actif ? 'Oui' : 'Non'}</td>
              <td><button onClick={() => deactivate(u.id)}>Désactiver</button></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
