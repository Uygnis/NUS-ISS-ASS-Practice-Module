import '../styles/HomePage.css'
import '../styles/NotificationsPage.css'
import TopNav from '../components/TopNav.jsx'

export default function NotificationsPage({ onLogout }) {
  return (
    <div className="page-shell notifications-page">
      <TopNav onLogout={onLogout} />
      <main className="page-main">
        <section className="hero-card notifications-card">
          <div className="hero-copy">
            <p className="eyebrow">NOTIFICATIONS</p>
            <h1>Notification centre</h1>
            <p className="hero-description">Your notifications will appear here once they arrive.</p>
          </div>
          <div className="hero-panels">
            <div className="hero-panel">
              <h2>No new alerts</h2>
              <p>Check back later for the latest updates and reservation notices.</p>
            </div>
            <div className="hero-panel hero-panel-emphasis">
              <h2>Tips</h2>
              <p>Use the navigation buttons above to manage reservations and profile settings.</p>
            </div>
          </div>
        </section>
      </main>
    </div>
  )
}
