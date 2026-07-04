import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { GraveyardView } from '../src/components/GraveyardView'
import * as linksApi from '../src/api/linksApi'

describe('GraveyardView', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows a graveyard-specific empty state with zero links', async () => {
    vi.spyOn(linksApi, 'fetchGraveyardLinks').mockResolvedValue([])

    render(<GraveyardView />)

    expect(await screen.findByText('Nothing in the graveyard yet.')).toBeInTheDocument()
  })

  it('renders links via LinkList in the order received', async () => {
    const links = [
      {
        id: 1,
        url: 'https://soonest.example.com',
        title: 'https://soonest.example.com',
        faviconUrl: null,
        savedAt: 'a',
        expiresAt: new Date(Date.now() + 86_400_000).toISOString(),
      },
      {
        id: 2,
        url: 'https://latest.example.com',
        title: 'https://latest.example.com',
        faviconUrl: null,
        savedAt: 'a',
        expiresAt: new Date(Date.now() + 10 * 86_400_000).toISOString(),
      },
    ]
    vi.spyOn(linksApi, 'fetchGraveyardLinks').mockResolvedValue(links)

    render(<GraveyardView />)

    const items = await screen.findAllByRole('listitem')
    expect(items).toHaveLength(2)
    expect(items[0]).toHaveTextContent('soonest.example.com')
    expect(items[1]).toHaveTextContent('latest.example.com')
  })
})
