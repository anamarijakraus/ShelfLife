import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LinkList } from '../src/components/LinkList'
import type { Link } from '../src/types/link'

function link(overrides: Partial<Link>): Link {
  return {
    id: 1,
    url: 'https://example.com',
    savedAt: new Date().toISOString(),
    expiresAt: new Date(Date.now() + 3_600_000).toISOString(),
    ...overrides,
  }
}

describe('LinkList', () => {
  it('shows an empty state with zero links', () => {
    render(<LinkList links={[]} />)
    expect(screen.getByText(/nothing saved yet/i)).toBeInTheDocument()
  })

  it('renders items in the order they are received', () => {
    const links = [
      link({ id: 1, url: 'https://soonest.example.com' }),
      link({ id: 2, url: 'https://latest.example.com' }),
    ]

    render(<LinkList links={links} />)
    const items = screen.getAllByRole('listitem')

    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('soonest.example.com')
    expect(items[1]).toHaveTextContent('latest.example.com')
  })
})
