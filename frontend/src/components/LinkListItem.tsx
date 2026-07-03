import type { Link } from '../types/link'

interface LinkListItemProps {
  link: Link
  granularity?: 'fine' | 'coarse'
  openable?: boolean
}

function formatRemainingTime(expiresAt: string): string {
  const remainingMs = new Date(expiresAt).getTime() - Date.now()
  if (remainingMs <= 0) {
    return 'Expiring…'
  }

  const totalMinutes = Math.floor(remainingMs / 60_000)
  const days = Math.floor(totalMinutes / (24 * 60))
  const hours = Math.floor((totalMinutes % (24 * 60)) / 60)
  const minutes = totalMinutes % 60

  if (days > 0) {
    return `${days}d ${hours}h left`
  }
  if (hours > 0) {
    return `${hours}h ${minutes}m left`
  }
  return `${minutes}m left`
}

// Coarser than formatRemainingTime: whole days while more than 1 day remains,
// then hour-level (no minutes) within the final day. Per FR-017, used only by
// the graveyard view, since its 30-day window is a lower-urgency grace period
// rather than the active list's core scarcity mechanic.
function formatRemainingTimeCoarse(expiresAt: string): string {
  const remainingMs = new Date(expiresAt).getTime() - Date.now()
  if (remainingMs <= 0) {
    return 'Expiring…'
  }

  const totalMinutes = Math.floor(remainingMs / 60_000)
  const days = Math.floor(totalMinutes / (24 * 60))

  if (days >= 1) {
    return `${days}d left`
  }

  const hours = Math.floor(totalMinutes / 60)
  return `${hours}h left`
}

export function LinkListItem({ link, granularity = 'fine', openable = false }: LinkListItemProps) {
  const remainingTime =
    granularity === 'coarse' ? formatRemainingTimeCoarse(link.expiresAt) : formatRemainingTime(link.expiresAt)

  return (
    <li className="card card-border">
      <div className="card-body flex-row items-center justify-between gap-4 py-3 px-4">
        {openable ? (
          <a
            href={link.url}
            target="_blank"
            rel="noopener noreferrer"
            className="truncate underline-offset-2 hover:underline"
            title={link.url}
          >
            {link.url}
          </a>
        ) : (
          <span className="truncate" title={link.url}>
            {link.url}
          </span>
        )}
        <span className="badge badge-neutral whitespace-nowrap">
          {remainingTime}
        </span>
      </div>
    </li>
  )
}
