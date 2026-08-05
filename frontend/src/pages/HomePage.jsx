import { useNavigate } from 'react-router-dom'
import TopNav from '../components/TopNav.jsx'
import '../styles/HomePage.css'

export default function HomePage({ onLogout }) {
  const navigate = useNavigate()

  return (
    <div className="page-shell home-page-layout">
      <TopNav onLogout={onLogout} />
      <main className="page-main">
        <section className="hero-card">
          <div className="hero-copy">
            <p className="eyebrow">DASHBOARD</p>
            <h1>Welcome Back to RentEZ</h1>
            <p className="hero-description">
              Everything you need to track bookings, view reservations, and manage your profile in one clean experience.
            </p>
          </div>
          <div className="hero-panels">
            <div className="hero-panel">
              <h2>Quick actions</h2>
              <p>Jump directly to reservations, notifications, and profile actions from the top navigation.</p>
            </div>
            <div className="hero-panel hero-panel-emphasis">
              <h2>Stay organized</h2>
              <p>Use the navigation bar to keep your account updated and your bookings within reach.</p>
            </div>
          </div>
        </section>
      </main>
    </div>
  )
}
