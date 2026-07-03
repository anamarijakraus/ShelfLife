import type { Link } from '../types/link'

interface LinkListItemProps {
  link: Link
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

export function LinkListItem({ link }: LinkListItemProps) {
  return (
    <li className="card card-border">
      <div className="card-body flex-row items-center justify-between gap-4 py-3 px-4">
        <span className="truncate" title={link.url}>
          {link.url}
        </span>
        <span className="badge badge-neutral whitespace-nowrap">
          {formatRemainingTime(link.expiresAt)}
        </span>
      </div>
    </li>
  )
}
