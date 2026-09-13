import { render, screen } from '@testing-library/react'
import { describe, it, expect } from 'vitest'
import Greeting from '../components/Greetings.jsx'

describe('Greeting', () => {
  it('renders Hello World', () => {
    render(<Greeting />)

    expect(screen.getByText('Hello World')).toBeInTheDocument()
  })
})