import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useGraveyardLinks } from '../src/hooks/useGraveyardLinks'
import * as linksApi from '../src/api/linksApi'

describe('useGraveyardLinks', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('fetches graveyard links on mount', async () => {
    const links = [
      { id: 1, url: 'https://example.com', title: 'https://example.com', faviconUrl: null, savedAt: 'a', expiresAt: 'b' },
    ]
    vi.spyOn(linksApi, 'fetchGraveyardLinks').mockResolvedValue(links)

    const { result } = renderHook(() => useGraveyardLinks())

    await waitFor(() => expect(result.current.links).toEqual(links))
    expect(linksApi.fetchGraveyardLinks).toHaveBeenCalledTimes(1)
  })

  it('refetches on the periodic ~60s tick', async () => {
    vi.spyOn(linksApi, 'fetchGraveyardLinks').mockResolvedValue([])

    renderHook(() => useGraveyardLinks())
    await waitFor(() => expect(linksApi.fetchGraveyardLinks).toHaveBeenCalledTimes(1))

    await vi.advanceTimersByTimeAsync(60_000)

    expect(linksApi.fetchGraveyardLinks).toHaveBeenCalledTimes(2)
  })
})
