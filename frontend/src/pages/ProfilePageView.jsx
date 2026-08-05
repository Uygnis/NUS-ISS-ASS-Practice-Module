import { useNavigate } from 'react-router-dom'
import TopNav from '../components/TopNav.jsx'
import '../styles/ProfilePageView.css'
import { ProfilePage } from '../components/ProfilePage.jsx'

export default function ProfilePageView({ profile, onSave, error, onLogout }) {
  const navigate = useNavigate()

  const handleSave = async (draft) => {
    await onSave(draft)
  }

  const handleBack = () => {
    navigate('/home')
  }

  return (
    <div className="page-shell profile-page-layout">
      <TopNav onLogout={onLogout} />
      <main className="page-main">
        <ProfilePage profile={profile} onSave={handleSave} onCancel={handleBack} error={error} />
      </main>
    </div>
  )
}
