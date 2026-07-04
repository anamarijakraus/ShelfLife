import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LinkList } from '../src/components/LinkList'
import { EmptyActiveIllustration } from '../src/components/EmptyActiveIllustration'
import { EmptyGraveyardIllustration } from '../src/components/EmptyGraveyardIllustration'
import type { Link } from '../src/types/link'

function link(overrides: Partial<Link>): Link {
  return {
    id: 1,
    url: 'https://example.com',
    title: 'https://example.com',
    faviconUrl: null,
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

  it('shows a custom empty message when provided, instead of the default', () => {
    render(<LinkList links={[]} emptyMessage="Nothing in the graveyard yet." />)
    expect(screen.getByText('Nothing in the graveyard yet.')).toBeInTheDocument()
    expect(screen.queryByText(/nothing saved yet/i)).not.toBeInTheDocument()
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

  it('shows the active-list illustration alongside the empty message when provided', () => {
    render(<LinkList links={[]} emptyIllustration={<EmptyActiveIllustration />} />)

    expect(screen.getByTestId('empty-active-illustration')).toBeInTheDocument()
    expect(screen.getByText(/nothing saved yet/i)).toBeInTheDocument()
  })

  it('shows a distinct graveyard illustration alongside its own empty message when provided', () => {
    render(
      <LinkList
        links={[]}
        emptyMessage="Nothing in the graveyard yet."
        emptyIllustration={<EmptyGraveyardIllustration />}
      />,
    )

    expect(screen.getByTestId('empty-graveyard-illustration')).toBeInTheDocument()
    expect(screen.queryByTestId('empty-active-illustration')).not.toBeInTheDocument()
    expect(screen.getByText('Nothing in the graveyard yet.')).toBeInTheDocument()
  })

  it('does not render any illustration slot when links are present', () => {
    render(<LinkList links={[link({})]} emptyIllustration={<EmptyActiveIllustration />} />)

    expect(screen.queryByTestId('empty-active-illustration')).not.toBeInTheDocument()
  })
})
