import { useEffect, useState } from 'react'
import { validateName, validatePhone } from '../utils/validation'

export function ProfilePage({ profile, onSave, onCancel, error }) {
  const [draft, setDraft] = useState({
    email: profile?.email || '',
    name: profile?.name || '',
    phone: profile?.phone || '',
  })
  const [isEditing, setIsEditing] = useState(false)

  useEffect(() => {
    setDraft({
      email: profile?.email || '',
      name: profile?.name || '',
      phone: profile?.phone || '',
    })
    setIsEditing(false)
  }, [profile])

  const nameError = validateName(draft.name)
  const phoneError = validatePhone(draft.phone)
  const canSave = !nameError && !phoneError && isEditing

  const handleSave = async () => {
    if (!canSave) return
    const success = await onSave(draft)
    if (success) {
      setIsEditing(false)
    }
  }

  const handleCancelEdit = () => {
    setDraft({
      email: profile?.email || '',
      name: profile?.name || '',
      phone: profile?.phone || '',
    })
    setIsEditing(false)
  }

  return (
    <div className="panel profile-panel">
      <h1>My Profile</h1>
      <label>
        Email
        <input value={draft.email} disabled className="disabled-input" />
      </label>
      <label>
        Full name
        <input
          value={draft.name}
          disabled={!isEditing}
          className={!isEditing ? 'disabled-input' : ''}
          onChange={(e) => setDraft({ ...draft, name: e.target.value })}
        />
        {isEditing && nameError && <div className="field-error">{nameError}</div>}
      </label>
      <label>
        Phone number
        <input
          value={draft.phone}
          disabled={!isEditing}
          className={!isEditing ? 'disabled-input' : ''}
          onChange={(e) => {
            const cleaned = e.target.value.replace(/\D/g, '').slice(0, 8)
            setDraft({ ...draft, phone: cleaned })
          }}
        />
        {isEditing && phoneError && <div className="field-error">{phoneError}</div>}
      </label>
      {error && <div className="form-error">{error}</div>}
      <div className="profile-actions">
        {isEditing ? (
          <>
            <button type="button" disabled={!canSave} onClick={handleSave}>
              Save profile
            </button>
            <button type="button" onClick={handleCancelEdit}>
              Cancel
            </button>
          </>
        ) : (
          <>
            <button type="button" onClick={() => setIsEditing(true)}>
              Edit profile
            </button>
            <button type="button" onClick={onCancel}>
              Back to home
            </button>
          </>
        )}
      </div>
    </div>
  )
}
