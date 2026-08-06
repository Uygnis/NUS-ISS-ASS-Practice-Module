import { useState } from 'react'
import { validateName, validatePhone, validateEmail, validatePassword } from '../utils/validation'

export function AuthForm({ type, onSubmit, error }) {
  const [values, setValues] = useState({
    email: '',
    password: '',
    name: '',
    phone: '',
  })
  const [submitted, setSubmitted] = useState(false)
  const [touched, setTouched] = useState({})

  const isRegister = type === 'register'

  // For registration we perform client-side validation and show field errors live.
  // For login, skip local validation and allow submit; backend will handle auth errors.
  const emailError = isRegister ? validateEmail(values.email) : ''
  const passwordError = isRegister ? validatePassword(values.password) : ''
  const nameError = isRegister ? validateName(values.name) : ''
  const phoneError = isRegister ? validatePhone(values.phone) : ''

  const canSubmit = () => {
    if (!isRegister) return true
    const e = validateEmail(values.email)
    const p = validatePassword(values.password)
    const n = validateName(values.name)
    const ph = validatePhone(values.phone)
    return !e && !p && !n && !ph
  }

  const handleChange = (field) => (event) => {
    let v = event.target.value
    if (field === 'phone') {
      v = v.replace(/\D/g, '').slice(0, 8)
    }
    if (field === 'name') {
      v = v.slice(0, 100)
    }
    if (field === 'password') {
      v = v.slice(0, 20)
    }
    setValues((current) => ({ ...current, [field]: v }))
  }

  const handleBlur = (field) => () => {
    setTouched((c) => ({ ...c, [field]: true }))
  }

  const handleSubmit = async (event) => {
    event.preventDefault()
    setSubmitted(true)
    if (!canSubmit()) return
    await onSubmit(values)
  }

  return (
    <form className="form-card" onSubmit={handleSubmit} noValidate>
      <h1>{isRegister ? 'Register an account' : 'Login to RentEZ'}</h1>
      {isRegister ? (
        <>
          <label>
            Full name
            <input
              type="text"
              value={values.name}
              onChange={handleChange('name')}
              onBlur={handleBlur('name')}
              required
              maxLength={100}
              aria-invalid={!!nameError}
            />
            {(submitted || touched.name) && nameError && <div className="field-error">{nameError}</div>}
          </label>
          <label>
            Password
            <input
              type="password"
              value={values.password}
              onChange={handleChange('password')}
              onBlur={handleBlur('password')}
              required
              minLength={8}
              maxLength={20}
              aria-invalid={!!passwordError}
            />
            {(submitted || touched.password) && passwordError && <div className="field-error">{passwordError}</div>}
          </label>
          <label>
            Email
            <input
              type="email"
              value={values.email}
              onChange={handleChange('email')}
              onBlur={handleBlur('email')}
              required
              aria-invalid={!!emailError}
            />
            {(submitted || touched.email) && emailError && <div className="field-error">{emailError}</div>}
          </label>
          <label>
            Phone number
            <input
              type="tel"
              value={values.phone}
              onChange={handleChange('phone')}
              onBlur={handleBlur('phone')}
              required
              maxLength={8}
              aria-invalid={!!phoneError}
            />
            {(submitted || touched.phone) && phoneError && <div className="field-error">{phoneError}</div>}
          </label>
        </>
      ) : (
        <>
          <label>
            Email
            <input
              type="email"
              value={values.email}
              onChange={handleChange('email')}
            />
          </label>
          <label>
            Password
            <input
              type="password"
              value={values.password}
              onChange={handleChange('password')}
              maxLength={20}
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
