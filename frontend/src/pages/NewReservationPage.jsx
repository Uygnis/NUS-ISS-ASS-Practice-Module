import TopNav from '../components/TopNav.jsx'
import '../styles/HomePage.css'

export default function NewReservationPage({ onLogout }) {
  return (
    <div className="page-shell new-reservation-page">
      <TopNav onLogout={onLogout} />
      <main className="page-main">
        <section className="hero-card">
          <div className="hero-copy">
            <p className="eyebrow">NEW RESERVATION</p>
            <h1>Create a new booking</h1>
            <p className="hero-description">
              Reserve your next trip by filling in the details. Your new reservation will appear in your list.
            </p>
          </div>
          <div className="hero-panels">
            <div className="hero-panel">
              <h2>Easy booking</h2>
              <p>Quickly add reservation details and confirm availability in one place.</p>
            </div>
            <div className="hero-panel hero-panel-emphasis">
              <h2>Ready when you are</h2>
              <p>Start a new reservation without leaving the dashboard.</p>
            </div>
          </div>
        </section>
      </main>
    </div>
  )
}
