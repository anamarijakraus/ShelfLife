import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { ActiveCount } from '../src/components/ActiveCount'
import type { Link } from '../src/types/link'

function link(id: number): Link {
  return { id, url: `https://example.com/${id}`, savedAt: 'a', expiresAt: 'b' }
}

describe('ActiveCount', () => {
  it('renders the count matching the number of links passed in', () => {
    render(<ActiveCount links={[link(1), link(2), link(3)]} />)
    expect(screen.getByText('3 links saved')).toBeInTheDocument()
  })

  it('updates when the links prop changes', () => {
    const { rerender } = render(<ActiveCount links={[link(1)]} />)
    expect(screen.getByText('1 link saved')).toBeInTheDocument()

    rerender(<ActiveCount links={[link(1), link(2)]} />)
    expect(screen.getByText('2 links saved')).toBeInTheDocument()
  })
})
