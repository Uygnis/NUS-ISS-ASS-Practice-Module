import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import '../styles/TopNav.css'

export default function TopNav({ onLogout }) {
  const [menuOpen, setMenuOpen] = useState(false)
  const navigate = useNavigate()

  const handleLogout = async () => {
    setMenuOpen(false)
    try {
      if (onLogout) await Promise.resolve(onLogout())
    } catch (err) {
      void err
    }
    navigate('/login', { replace: true })
  }

  return (
    <nav className="top-nav">
      <div className="top-nav-filler" />
      <Link to="/home" className="brand-link">
        RentEZ
      </Link>
      <div className="top-nav-actions">
        <button type="button" className="secondary-button" onClick={() => navigate('/reservations')}>
          My reservations
        </button>
        <button type="button" className="primary-button" onClick={() => navigate('/new-reservation')}>
          + New reservation
        </button>
        <button
          type="button"
          className="icon-button"
          aria-label="Notifications"
          onClick={() => navigate('/notifications')}
        >
          🔔
        </button>
        <div className="profile-dropdown-wrapper">
          <button
            type="button"
            className="profile-trigger"
            aria-expanded={menuOpen}
            onClick={() => setMenuOpen((current) => !current)}
          >
            <span className="profile-icon">👤</span>
          </button>
          {menuOpen && (
            <div className="profile-dropdown-menu">
              <button type="button" onClick={() => { setMenuOpen(false); navigate('/profile') }}>
                View profile
              </button>
              <button type="button" onClick={handleLogout}>
                Logout
              </button>
            </div>
          )}
        </div>
      </div>
    </nav>
  )
}
