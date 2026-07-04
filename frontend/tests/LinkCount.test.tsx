import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LinkCount } from '../src/components/LinkCount'
import type { Link } from '../src/types/link'

function link(id: number): Link {
  return {
    id,
    url: `https://example.com/${id}`,
    title: `https://example.com/${id}`,
    faviconUrl: null,
    savedAt: 'a',
    expiresAt: 'b',
  }
}

describe('LinkCount', () => {
  it('renders the count matching the number of links passed in', () => {
    render(<LinkCount links={[link(1), link(2), link(3)]} />)
    expect(screen.getByText('3 links on the shelf')).toBeInTheDocument()
  })

  it('updates when the links prop changes', () => {
    const { rerender } = render(<LinkCount links={[link(1)]} />)
    expect(screen.getByText('1 link on the shelf')).toBeInTheDocument()

    rerender(<LinkCount links={[link(1), link(2)]} />)
    expect(screen.getByText('2 links on the shelf')).toBeInTheDocument()
  })

  it('renders a custom label in place of the default "on the shelf" text', () => {
    render(<LinkCount links={[link(1), link(2)]} label="decaying" />)
    expect(screen.getByText('2 links decaying')).toBeInTheDocument()
  })
})
