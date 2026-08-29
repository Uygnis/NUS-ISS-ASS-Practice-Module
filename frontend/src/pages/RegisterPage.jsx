import { useNavigate } from 'react-router-dom'
import '../styles/RegisterPage.css'
import { AuthForm } from '../components/AuthForm.jsx'

export default function RegisterPage({ onRegister, error }) {
  const navigate = useNavigate()

  const handleSubmit = async (values) => {
    const success = await onRegister(values)
    if (success) {
      navigate('/registered')
    }
  }

  return (
    <div className="page-shell register-page">
      <div className="panel register-panel">
        <AuthForm type="register" onSubmit={handleSubmit} error={error} />
        <div className="form-footer">
          <button onClick={() => navigate('/login')} type="button">
            Back to login
          </button>
        </div>
      </div>
    </div>
  )
}
