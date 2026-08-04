import { useState } from 'react'

export function ProfilePage({ profile, onSave, onCancel, error }) {
  const initialDraft = {
    email: profile?.email || '',
    name: profile?.name || '',
    phone: profile?.phone || '',
  }
  const [draft, setDraft] = useState(initialDraft)
  const canSave = draft.name.trim().length >= 2 && draft.phone.trim().length >= 8

  return (
    <div className="page-shell">
      <div className="panel">
        <h1>My Profile</h1>
        <label>
          Email
          <input value={draft.email} disabled />
        </label>
        <label>
          Full name
          <input
            value={draft.name}
            onChange={(e) => setDraft({ ...draft, name: e.target.value })}
          />
        </label>
        <label>
          Phone number
          <input
            value={draft.phone}
            onChange={(e) => setDraft({ ...draft, phone: e.target.value })}
          />
        </label>
        {error && <div className="form-error">{error}</div>}
        <div className="profile-actions">
          <button type="button" disabled={!canSave} onClick={() => onSave(draft)}>
            Save profile
          </button>
          <button type="button" onClick={onCancel}>
            Back to home
          </button>
        </div>
      </div>
    </div>
  )
}
