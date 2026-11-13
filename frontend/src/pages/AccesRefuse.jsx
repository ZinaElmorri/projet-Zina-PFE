import { Link } from 'react-router-dom'
import { Button, Result } from 'antd'

export default function AccesRefuse() {
  return (
    <Result
      status="403"
      title="Accès refusé"
      subTitle="Votre rôle ne permet pas d’accéder à cette page."
      extra={<Link to="/"><Button type="primary">Retour au tableau de bord</Button></Link>}
    />
  )
}
