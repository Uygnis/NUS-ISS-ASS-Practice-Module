import { useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate, useNavigate } from 'react-router-dom'
import './App.css'
import { AuthForm } from './components/AuthForm.jsx'
import { ProfilePage } from './components/ProfilePage.jsx'

const initialUser = { isAuthenticated: false, profile: null }

function App() {
  const [user, setUser] = useState(initialUser)
  const [error, setError] = useState('')
  const [successMessage, setSuccessMessage] = useState('')
  const backendUrl = import.meta.env.VITE_API_URL || 'http://localhost:8080'

  const handleLogin = async ({ email, password }) => {
    setError('')
    try {
      const response = await fetch(`${backendUrl}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password }),
      })
      const data = await response.json()
      if (!response.ok) {
        setError(data.message || 'Invalid credentials')
        return false
      }
      setUser({ isAuthenticated: true, profile: data })
      return true
    } catch {
      setError('Unable to reach authentication service.')
      return false
    }
  }

  const handleRegister = async ({ email, password, name, phone }) => {
    setError('')
    try {
      const response = await fetch(`${backendUrl}/api/auth/register`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password, name, phone }),
      })
      const data = await response.json()
      if (!response.ok) {
        setError(data.message || 'Registration failed')
        return false
      }
      setSuccessMessage('Registration successful. Press OK to continue to login.')
      return true
    } catch {
      setError('Unable to reach registration service.')
      return false
    }
  }

  const handleProfileSave = async (profile) => {
    setError('')
    try {
      const response = await fetch(`${backendUrl}/api/auth/profile`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(profile),
      })
      const data = await response.json()
      if (!response.ok) {
        setError(data.message || 'Unable to save profile')
        return false
      }
      setUser((current) => ({ ...current, profile: data }))
      return true
    } catch {
      setError('Unable to reach profile service.')
      return false
    }
  }

  const handleLogout = () => {
    setUser(initialUser)
  }

  return (
    <BrowserRouter>
      <div className="app-root">
        <Routes>
          <Route path="/" element={<Navigate to={user.isAuthenticated ? '/home' : '/login'} replace />} />
          <Route
            path="/login"
            element={
              user.isAuthenticated ? (
                <Navigate to="/home" replace />
              ) : (
                <LoginPage onLogin={handleLogin} error={error} />
              )
            }
          />
          <Route
            path="/register"
            element={
              user.isAuthenticated ? (
                <Navigate to="/home" replace />
              ) : (
                <RegisterPage onRegister={handleRegister} error={error} />
              )
            }
          />
          <Route
            path="/registered"
            element={
              user.isAuthenticated ? <Navigate to="/home" replace /> : <RegisteredPage message={successMessage} />
            }
          />
          <Route
            path="/home"
            element={
              user.isAuthenticated ? (
                <HomePage onLogout={handleLogout} />
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          <Route
            path="/profile"
            element={
              user.isAuthenticated ? (
                <ProfilePageWrapper
                  profile={user.profile}
                  onSave={handleProfileSave}
                  error={error}
                />
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </div>
    </BrowserRouter>
  )
}

function LoginPage({ onLogin, error }) {
  const navigate = useNavigate()

  const handleSubmit = async (values) => {
    const success = await onLogin(values)
    if (success) {
      navigate('/home')
    }
  }

  return (
    <div className="page-shell">
      <div className="panel">
        <AuthForm type="login" onSubmit={handleSubmit} error={error} />
        <div className="form-footer">
          <button onClick={() => navigate('/register')} type="button">
            Create an account
          </button>
        </div>
      </div>
    </div>
  )
}

function RegisterPage({ onRegister, error }) {
  const navigate = useNavigate()

  const handleSubmit = async (values) => {
    const success = await onRegister(values)
    if (success) {
      navigate('/registered')
    }
  }

  return (
    <div className="page-shell">
      <div className="panel">
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

function RegisteredPage({ message }) {
  const navigate = useNavigate()

  return (
    <div className="page-shell">
      <div className="panel success-panel">
        <h1>Registration complete</h1>
        <p>{message}</p>
        <button onClick={() => navigate('/login')}>OK</button>
      </div>
    </div>
  )
}

function HomePage({ onLogout }) {
  const navigate = useNavigate()

  return (
    <div className="page-shell">
      <div className="panel">
        <header className="home-header">
          <h1>Welcome to RentEZ</h1>
          <p>Manage rentals, track vehicle availability, and keep your profile current.</p>
        </header>
        <div className="home-actions">
          <button onClick={() => navigate('/profile')}>View profile</button>
          <button onClick={onLogout}>Logout</button>
        </div>
        <div className="home-card">
          <h2>Ready to manage rentals</h2>
          <p>
            RentEZ helps car rental teams centralize bookings, customers, and vehicle availability in one cloud-native portal.
          </p>
        </div>
      </div>
    </div>
  )
}

function ProfilePageWrapper({ profile, onSave, error }) {
  const navigate = useNavigate()

  const handleSave = async (draft) => {
    const success = await onSave(draft)
    if (success) {
      navigate('/home')
    }
  }

  const handleBack = () => {
    navigate('/home')
  }

  return (
    <ProfilePage profile={profile} onSave={handleSave} onCancel={handleBack} error={error} />
  )
}

export default App
