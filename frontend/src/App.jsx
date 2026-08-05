import { useState } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import './styles/App.css'
import LoginPage from './pages/LoginPage.jsx'
import RegisterPage from './pages/RegisterPage.jsx'
import RegisteredPage from './pages/RegisteredPage.jsx'
import HomePage from './pages/HomePage.jsx'
import ProfilePageView from './pages/ProfilePageView.jsx'
import NotificationsPage from './pages/NotificationsPage.jsx'

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
            path="/notifications"
            element={
              user.isAuthenticated ? (
                <NotificationsPage onLogout={handleLogout} />
              ) : (
                <Navigate to="/login" replace />
              )
            }
          />
          <Route
            path="/profile"
            element={
              user.isAuthenticated ? (
                <ProfilePageView profile={user.profile} onSave={handleProfileSave} error={error} />
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

export default App
