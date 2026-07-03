import { useCallback, useEffect, useState } from 'react'
import { fetchActiveLinks } from '../api/linksApi'
import type { Link } from '../types/link'

const REFRESH_INTERVAL_MS = 60_000

export function useActiveLinks() {
  const [links, setLinks] = useState<Link[]>([])

  const refresh = useCallback(() => {
    fetchActiveLinks()
      .then(setLinks)
      .catch(() => {
        // Keep showing the last known list on a transient fetch failure.
      })
  }, [])

  useEffect(() => {
    refresh()
    const interval = setInterval(refresh, REFRESH_INTERVAL_MS)
    return () => clearInterval(interval)
  }, [refresh])

  return { links, refresh }
}
