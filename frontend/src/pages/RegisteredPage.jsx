import { useNavigate } from 'react-router-dom'
import '../styles/RegisteredPage.css'

export default function RegisteredPage({ message }) {
  const navigate = useNavigate()

  return (
    <div className="page-shell registered-page">
      <div className="panel success-panel registered-panel">
        <h1>Registration complete</h1>
        <p>{message}</p>
        <button onClick={() => navigate('/login')}>OK</button>
      </div>
    </div>
  )
}
