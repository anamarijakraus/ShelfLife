import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { LinkListItem } from '../src/components/LinkListItem'
import type { Link } from '../src/types/link'
import * as linksApi from '../src/api/linksApi'

function link(overrides: Partial<Link>): Link {
  return {
    id: 1,
    url: 'https://example.com',
    title: 'Example Title',
    faviconUrl: 'https://www.google.com/s2/favicons?domain=example.com&sz=64',
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

  it('renders a plain, non-clickable heading and secondary url by default', () => {
    render(<LinkListItem link={link({ url: 'https://example.com/plain' })} />)

    expect(screen.getByText('Example Title')).toBeInTheDocument()
    expect(screen.getByText('https://example.com/plain')).toBeInTheDocument()
    expect(screen.queryByRole('link')).not.toBeInTheDocument()
  })

  it('renders a new-tab, no-opener anchor around the title (not the url) when openable is true', () => {
    render(<LinkListItem link={link({ url: 'https://example.com/openable', title: 'Openable Title' })} openable />)

    const anchor = screen.getByRole('link', { name: 'Openable Title' })
    expect(anchor).toHaveAttribute('href', 'https://example.com/openable')
    expect(anchor).toHaveAttribute('target', '_blank')
    expect(anchor).toHaveAttribute('rel', 'noopener noreferrer')
    expect(screen.getByText('https://example.com/openable')).not.toHaveAttribute('href')
  })

  it('shows the raw url as the heading when it is used as the title fallback', () => {
    render(<LinkListItem link={link({ url: 'https://example.com/fallback', title: 'https://example.com/fallback' })} />)

    expect(screen.getAllByText('https://example.com/fallback')).toHaveLength(2)
  })

  it('renders the favicon image when faviconUrl is present', () => {
    render(<LinkListItem link={link({ faviconUrl: 'https://www.google.com/s2/favicons?domain=example.com&sz=64' })} />)

    const favicon = screen.getByTestId('favicon-image')
    expect(favicon).toHaveAttribute('src', 'https://www.google.com/s2/favicons?domain=example.com&sz=64')
    expect(screen.queryByTestId('favicon-fallback')).not.toBeInTheDocument()
  })

  it('renders the generic fallback icon when faviconUrl is null', () => {
    render(<LinkListItem link={link({ faviconUrl: null })} />)

    expect(screen.getByTestId('favicon-fallback')).toBeInTheDocument()
    expect(screen.queryByTestId('favicon-image')).not.toBeInTheDocument()
  })

  it('swaps to the generic fallback icon when the favicon image fails to load', () => {
    render(<LinkListItem link={link({ faviconUrl: 'https://www.google.com/s2/favicons?domain=broken.example.com&sz=64' })} />)

    const favicon = screen.getByTestId('favicon-image')
    fireEvent.error(favicon)

    expect(screen.getByTestId('favicon-fallback')).toBeInTheDocument()
    expect(screen.queryByTestId('favicon-image')).not.toBeInTheDocument()
  })

  it('arms the delete control on first activation without deleting', () => {
    const onDeleted = vi.fn()
    render(<LinkListItem link={link({ id: 7 })} onDeleted={onDeleted} />)

    fireEvent.click(screen.getByRole('button', { name: 'Delete link' }))

    expect(screen.getByRole('button', { name: 'Confirm delete' })).toBeInTheDocument()
    expect(onDeleted).not.toHaveBeenCalled()
  })

  it('calls onDeleted with the link id after the delete control is armed and confirmed', async () => {
    vi.useRealTimers()
    const onDeleted = vi.fn()
    vi.spyOn(linksApi, 'deleteLink').mockResolvedValue(undefined)

    render(<LinkListItem link={link({ id: 7 })} onDeleted={onDeleted} />)
    fireEvent.click(screen.getByRole('button', { name: 'Delete link' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delete' }))

    await waitFor(() => expect(onDeleted).toHaveBeenCalledWith(7))
  })

  it('shows a fresh hourglass motif and calm badge when far from expiring', () => {
    const expiresAt = new Date(fixedNow.getTime() + 100 * 60 * 60 * 1000).toISOString()
    render(<LinkListItem link={link({ expiresAt })} />)

    expect(screen.getByTestId('hourglass-motif')).toHaveAttribute('data-urgency', 'fresh')
    expect(screen.getByText('4d 4h left')).toBeInTheDocument()
  })

  it('shows a turning hourglass motif partway through the countdown', () => {
    const expiresAt = new Date(fixedNow.getTime() + 40 * 60 * 60 * 1000).toISOString()
    render(<LinkListItem link={link({ expiresAt })} />)

    expect(screen.getByTestId('hourglass-motif')).toHaveAttribute('data-urgency', 'turning')
    expect(screen.getByText('1d 16h left')).toBeInTheDocument()
  })

  it('shows a wilted hourglass motif close to expiring, with the countdown text unaffected', () => {
    const expiresAt = new Date(fixedNow.getTime() + 5 * 60 * 60 * 1000).toISOString()
    render(<LinkListItem link={link({ expiresAt })} />)

    expect(screen.getByTestId('hourglass-motif')).toHaveAttribute('data-urgency', 'wilted')
    expect(screen.getByText('5h 0m left')).toBeInTheDocument()
  })

  it('computes the urgency band from the graveyard\'s 30-day window in coarse mode', () => {
    const expiresAt = new Date(fixedNow.getTime() + 2 * 24 * 60 * 60 * 1000).toISOString()
    render(<LinkListItem link={link({ expiresAt })} granularity="coarse" />)

    expect(screen.getByTestId('hourglass-motif')).toHaveAttribute('data-urgency', 'wilted')
    expect(screen.getByText('2d left')).toBeInTheDocument()
  })

  it('truncates a very long title and a very long url instead of breaking the card layout', () => {
    const longTitle = 'A '.repeat(200).trim()
    const longUrl = 'https://example.com/' + 'a'.repeat(400)
    render(<LinkListItem link={link({ title: longTitle, url: longUrl })} />)

    const heading = screen.getByText(longTitle)
    const secondaryUrl = screen.getByText(longUrl)

    expect(heading).toHaveClass('truncate')
    expect(secondaryUrl).toHaveClass('truncate')
    expect(heading.closest('.flex')).toHaveClass('min-w-0')
  })
})
