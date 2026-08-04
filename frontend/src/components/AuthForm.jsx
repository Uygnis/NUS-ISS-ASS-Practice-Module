import { useState } from 'react'

export function AuthForm({ type, onSubmit, error }) {
  const [values, setValues] = useState({
    email: '',
    password: '',
    name: '',
    phone: '',
  })
  const [submitted, setSubmitted] = useState(false)

  const isRegister = type === 'register'
  const canSubmit = () => {
    if (!values.email || !values.password) return false
    if (isRegister) {
      return values.name.trim().length >= 2 && values.phone.trim().length >= 8
    }
    return true
  }

  const handleChange = (field) => (event) => {
    setValues((current) => ({ ...current, [field]: event.target.value }))
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setSubmitted(true)
    if (!canSubmit()) return
    onSubmit(values)
  }

  return (
    <form className="form-card" onSubmit={handleSubmit} noValidate>
      <h1>{isRegister ? 'Register an account' : 'Login to RentEZ'}</h1>
      <label>
        Email
        <input
          type="email"
          value={values.email}
          onChange={handleChange('email')}
          required
          aria-invalid={submitted && !values.email}
        />
      </label>
      <label>
        Password
        <input
          type="password"
          value={values.password}
          onChange={handleChange('password')}
          required
          minLength={6}
          aria-invalid={submitted && values.password.length < 6}
        />
      </label>
      {isRegister && (
        <>
          <label>
            Full name
            <input
              type="text"
              value={values.name}
              onChange={handleChange('name')}
              required
              aria-invalid={submitted && values.name.trim().length < 2}
            />
          </label>
          <label>
            Phone number
            <input
              type="tel"
              value={values.phone}
              onChange={handleChange('phone')}
              required
              minLength={8}
              aria-invalid={submitted && values.phone.trim().length < 8}
            />
          </label>
        </>
      )}
      {error && <div className="form-error">{error}</div>}
      <button type="submit" disabled={!canSubmit()}>
        {isRegister ? 'Register' : 'Login'}
      </button>
    </form>
  )
}
