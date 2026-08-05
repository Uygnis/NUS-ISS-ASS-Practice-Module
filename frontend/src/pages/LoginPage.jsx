import { useNavigate } from 'react-router-dom'
import { useEffect } from 'react'
import '../styles/LoginPage.css'
import { AuthForm } from '../components/AuthForm.jsx'

export default function LoginPage({ onLogin, error }) {
  const navigate = useNavigate()

  const handleSubmit = async (values) => {
    const success = await onLogin(values)
    if (success) {
      navigate('/home')
    }
  }

  useEffect(() => {
    if (error) {
      try {
        alert(error)
      } catch (err) {
        void err
      }
    }
  }, [error])

  return (
    <div className="page-shell login-page">
      <div className="panel login-panel">
        <AuthForm type="login" onSubmit={handleSubmit} />
        <div className="auth-footer-text">Don't have an account with us?</div>
        <div className="form-footer">
          <button onClick={() => navigate('/register')} type="button">
            Create an account
          </button>
        </div>
      </div>
    </div>
  )
}
