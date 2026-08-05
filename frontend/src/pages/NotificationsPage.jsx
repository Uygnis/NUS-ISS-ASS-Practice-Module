import '../styles/NotificationsPage.css'
import TopNav from '../components/TopNav.jsx'

export default function NotificationsPage({ onLogout }) {
  return (
    <div className="page-shell notifications-page">
      <TopNav onLogout={onLogout} />
      <main className="page-main">
        <section className="notifications-card">
          <h1>Notification centre</h1>
          <p>Your notifications will appear here.</p>
          <div className="notification-empty">
            <p>No new notifications yet.</p>
          </div>
        </section>
      </main>
    </div>
  )
}
