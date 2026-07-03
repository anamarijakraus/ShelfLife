import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { LinkListItem } from '../src/components/LinkListItem'
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

describe('LinkListItem', () => {
  const fixedNow = new Date('2026-07-03T00:00:00.000Z')

  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(fixedNow)
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('uses the active list\'s fine-grained day/hour formatting by default', () => {
    const expiresAt = new Date(fixedNow.getTime() + 26 * 60 * 60 * 1000).toISOString()
    render(<LinkListItem link={link({ expiresAt })} />)

    expect(screen.getByText('1d 2h left')).toBeInTheDocument()
  })

  it('shows whole-day granularity in coarse mode while more than 1 day remains', () => {
    const expiresAt = new Date(fixedNow.getTime() + 12 * 24 * 60 * 60 * 1000).toISOString()
    render(<LinkListItem link={link({ expiresAt })} granularity="coarse" />)

    expect(screen.getByText('12d left')).toBeInTheDocument()
  })

  it('switches to hour-level (no minutes) granularity within the final day in coarse mode', () => {
    const expiresAt = new Date(fixedNow.getTime() + 18 * 60 * 60 * 1000).toISOString()
    render(<LinkListItem link={link({ expiresAt })} granularity="coarse" />)

    expect(screen.getByText('18h left')).toBeInTheDocument()
  })

  it('renders a plain, non-clickable label by default', () => {
    render(<LinkListItem link={link({ url: 'https://example.com/plain' })} />)

    expect(screen.getByText('https://example.com/plain')).toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })

  it('renders a new-tab, no-opener anchor when openable is true', () => {
    render(<LinkListItem link={link({ url: 'https://example.com/openable' })} openable />)

    const anchor = screen.getByRole('link', { name: 'https://example.com/openable' })
    expect(anchor).toHaveAttribute('href', 'https://example.com/openable')
    expect(anchor).toHaveAttribute('target', '_blank')
    expect(anchor).toHaveAttribute('rel', 'noopener noreferrer')
  })
})
