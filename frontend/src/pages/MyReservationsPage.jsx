import TopNav from '../components/TopNav.jsx'
import '../styles/HomePage.css'

export default function MyReservationsPage({ onLogout }) {
  return (
    <div className="page-shell reservations-page">
      <TopNav onLogout={onLogout} />
      <main className="page-main">
        <section className="hero-card">
          <div className="hero-copy">
            <p className="eyebrow">MY RESERVATIONS</p>
            <h1>Your reservations</h1>
            <p className="hero-description">
              View and manage your current bookings in one place. Select a reservation to update or cancel.
            </p>
          </div>
          <div className="hero-panels">
            <div className="hero-panel">
              <h2>Upcoming trips</h2>
              <p>See your reservations and manage any pending actions.</p>
            </div>
            <div className="hero-panel hero-panel-emphasis">
              <h2>Stay organized</h2>
              <p>Keep your bookings up to date and your plan ready.</p>
            </div>
          </div>
        </section>
      </main>
    </div>
  )
}
