import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useActiveLinks } from '../src/hooks/useActiveLinks'
import * as linksApi from '../src/api/linksApi'

describe('useActiveLinks', () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
  })

  afterEach(() => {
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('fetches active links on mount', async () => {
    const links = [
      { id: 1, url: 'https://example.com', title: 'https://example.com', faviconUrl: null, savedAt: 'a', expiresAt: 'b' },
    ]
    vi.spyOn(linksApi, 'fetchActiveLinks').mockResolvedValue(links)

    const { result } = renderHook(() => useActiveLinks())

    await waitFor(() => expect(result.current.links).toEqual(links))
    expect(linksApi.fetchActiveLinks).toHaveBeenCalledTimes(1)
  })

  it('refetches on the periodic ~60s tick', async () => {
    vi.spyOn(linksApi, 'fetchActiveLinks').mockResolvedValue([])

    renderHook(() => useActiveLinks())
    await waitFor(() => expect(linksApi.fetchActiveLinks).toHaveBeenCalledTimes(1))

    await vi.advanceTimersByTimeAsync(60_000)

    expect(linksApi.fetchActiveLinks).toHaveBeenCalledTimes(2)
  })
})
