import { useEffect, useState } from 'react'
import api from '../api/client'

export default function Audit() {
  const [logs, setLogs] = useState([])

  useEffect(() => {
    api.get('/audit', { params: { size: 100, sort: 'horodatage,desc' } })
      .then(({ data }) => setLogs(data.content || []))
      .catch(() => setLogs([]))
  }, [])

  return (
    <div>
      <h2>Journal d'audit</h2>
      <table>
        <thead>
          <tr>
            <th>Horodatage</th><th>Utilisateur</th><th>Action</th>
            <th>Détails</th><th>IP</th><th>Résultat</th>
          </tr>
        </thead>
        <tbody>
          {logs.map((l) => (
            <tr key={l.id}>
              <td>{new Date(l.horodatage).toLocaleString('fr-FR')}</td>
              <td>{l.username}</td>
              <td>{l.action}</td>
              <td>{l.details}</td>
              <td>{l.adresseIp}</td>
              <td>{l.resultat}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
