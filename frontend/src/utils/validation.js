export const validateName = (name) => {
  if (!name.trim() || name.trim().length < 2) {
    return 'Name must be at least 2 characters.'
  }
  if (name.length > 100) {
    return 'Name cannot exceed 100 characters.'
  }
  if (/[0-9]/.test(name)) {
    return 'Name cannot contain digits.'
  }
  return ''
}

export const validatePhone = (phone) => {
  if (!phone || !phone.trim()) return 'Phone number is required.'
  if (!/^[0-9]{8}$/.test(phone)) return 'Phone number must be exactly 8 digits.'
  return ''
}

export const validateEmail = (email) => {
  if (!email || !email.trim()) return 'Email is required.'
  const re = /^\S+@\S+\.\S+$/
  if (!re.test(email)) return 'Enter a valid email address.'
  return ''
}

export const validatePassword = (pw) => {
  if (!pw) return 'Password is required.'
  if (pw.length < 8) return 'Password must be at least 8 characters.'
  if (pw.length > 20) return 'Password cannot exceed 20 characters.'
  return ''
}
