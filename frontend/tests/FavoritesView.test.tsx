import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { FavoritesView } from '../src/components/FavoritesView'
import * as linksApi from '../src/api/linksApi'

describe('FavoritesView', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the favorites-specific illustrated empty state with zero links', async () => {
    vi.spyOn(linksApi, 'fetchFavoriteLinks').mockResolvedValue([])

    render(<FavoritesView />)

    expect(await screen.findByText('Nothing pinned yet.')).toBeInTheDocument()
    expect(screen.getByTestId('empty-favorites-illustration')).toBeInTheDocument()
  })

  it('renders pinned links via LinkList with an accurate count', async () => {
    const links = [
      {
        id: 1,
        url: 'https://pinned-one.example.com',
        title: 'https://pinned-one.example.com',
        faviconUrl: null,
        savedAt: 'a',
        expiresAt: null,
      },
      {
        id: 2,
        url: 'https://pinned-two.example.com',
        title: 'https://pinned-two.example.com',
        faviconUrl: null,
        savedAt: 'a',
        expiresAt: null,
      },
    ]
    vi.spyOn(linksApi, 'fetchFavoriteLinks').mockResolvedValue(links)

    render(<FavoritesView />)

    const items = await screen.findAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('pinned-one.example.com')
    expect(items[1]).toHaveTextContent('pinned-two.example.com')
    expect(screen.getByText('2 links pinned')).toBeInTheDocument()
  })
})
