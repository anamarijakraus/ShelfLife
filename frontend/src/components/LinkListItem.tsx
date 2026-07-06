import { useState } from 'react'
import type { Link } from '../types/link'
import { DeleteControl } from './DeleteControl'
import { HourglassMotif, type UrgencyBand } from './HourglassMotif'
import { PinControl } from './PinControl'
import { PinnedBadge } from './PinnedBadge'

interface LinkListItemProps {
  link: Link
  index?: number
  granularity?: 'fine' | 'coarse'
  openable?: boolean
  pinned?: boolean
  onDeleted?: (id: number) => void
  onPinToggled?: (id: number) => void
}

function FaviconFallback() {
  return (
    <svg
      data-testid="favicon-fallback"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.5"
      aria-hidden="true"
      className="h-5 w-5 flex-shrink-0 text-base-content/40"
    >
      <path
        strokeLinecap="round"
        strokeLinejoin="round"
        d="M13.19 8.688a4.5 4.5 0 0 1 1.242 7.244l-4.5 4.5a4.5 4.5 0 0 1-6.364-6.364l1.757-1.757m1.622 3.622 1.757-1.757a4.5 4.5 0 0 0-6.364-6.364l-4.5 4.5a4.5 4.5 0 0 0 1.242 7.244"
      />
    </svg>
  )
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

// Presentation-only: the underlying countdown value/expiry moment is untouched (FR-012). The
// band boundaries — and the bottom progress bar's length — are fractions of each view's own
// total lifespan, so the same computation works identically for the active list's 168h window
// and the graveyard's 30-day window (research.md §8).
const ACTIVE_TOTAL_DURATION_MS = 168 * 60 * 60 * 1000
const GRAVEYARD_TOTAL_DURATION_MS = 30 * 24 * 60 * 60 * 1000

function computeRemainingFraction(expiresAt: string, granularity: 'fine' | 'coarse'): number {
  const totalDuration = granularity === 'coarse' ? GRAVEYARD_TOTAL_DURATION_MS : ACTIVE_TOTAL_DURATION_MS
  const remainingMs = new Date(expiresAt).getTime() - Date.now()
  return Math.min(1, Math.max(0, remainingMs / totalDuration))
}

function bandFromFraction(remainingFraction: number): UrgencyBand {
  if (remainingFraction >= 0.5) {
    return 'fresh'
  }
  if (remainingFraction >= 0.1) {
    return 'turning'
  }
  return 'wilted'
}

const PILL_CLASS_BY_BAND: Record<UrgencyBand, string> = {
  fresh: 'bg-secondary',
  turning: 'bg-accent/80',
  wilted: 'bg-accent',
}

const BAR_CLASS_BY_BAND: Record<UrgencyBand, string> = {
  fresh: 'bg-primary',
  turning: 'bg-accent/80',
  wilted: 'bg-accent',
}

const CARD_BG_CLASS: Record<'fine' | 'coarse', string> = {
  fine: 'bg-[var(--color-shelf-card)]',
  coarse: 'bg-[var(--color-graveyard-card)]',
}

export function LinkListItem({
  link,
  index,
  granularity = 'fine',
  openable = false,
  pinned = false,
  onDeleted,
  onPinToggled,
}: LinkListItemProps) {
  const [faviconFailed, setFaviconFailed] = useState(false)

  // A pinned card's link.expiresAt is always null (the countdown concept doesn't apply while
  // pinned, per FR-006/research.md §5) — the countdown/urgency computations below are skipped
  // entirely in that case, in favor of the static PinnedBadge.
  const remainingTime =
    !pinned && link.expiresAt !== null
      ? granularity === 'coarse'
        ? formatRemainingTimeCoarse(link.expiresAt)
        : formatRemainingTime(link.expiresAt)
      : null
  const remainingFraction = !pinned && link.expiresAt !== null ? computeRemainingFraction(link.expiresAt, granularity) : 0
  const urgencyBand = bandFromFraction(remainingFraction)

  const showFavicon = link.faviconUrl !== null && !faviconFailed

  return (
    <li className={`card overflow-hidden rounded-box border-2 border-primary shadow-md ${CARD_BG_CLASS[granularity]}`}>
      <div className="card-body flex-row items-center justify-between gap-4 py-4 px-5">
        {index !== undefined && (
          <span aria-hidden="true" className="select-none font-serif text-2xl italic text-base-content/30">
            {String(index).padStart(2, '0')}
          </span>
        )}
        <div className="flex min-w-0 flex-1 items-center gap-3">
          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-lg border border-base-300 bg-base-100">
            {showFavicon ? (
              <img
                data-testid="favicon-image"
                src={link.faviconUrl!}
                onError={() => setFaviconFailed(true)}
                alt=""
                className="h-5 w-5 flex-shrink-0 rounded-sm"
              />
            ) : (
              <FaviconFallback />
            )}
          </div>
          <div className="flex min-w-0 flex-col">
            {openable ? (
              <a
                href={link.url}
                target="_blank"
                rel="noopener noreferrer"
                className="truncate font-semibold text-primary underline-offset-2 hover:underline"
                title={link.title}
              >
                {link.title}
              </a>
            ) : (
              <span className="truncate font-semibold text-primary" title={link.title}>
                {link.title}
              </span>
            )}
            <span className="truncate text-sm text-base-content/60" title={link.url}>
              {link.url}
            </span>
          </div>
        </div>
        <div className="flex items-center gap-2">
          {pinned ? (
            <PinnedBadge />
          ) : (
            <span
              className={`inline-flex items-center gap-2 whitespace-nowrap rounded-full py-0.5 pl-0.5 pr-3 text-sm font-medium text-primary-content ${PILL_CLASS_BY_BAND[urgencyBand]}`}
            >
              <span className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full border border-base-content/30 bg-[var(--color-badge-circle)]">
                <HourglassMotif urgency={urgencyBand} />
              </span>
              {remainingTime}
            </span>
          )}
          <PinControl linkId={link.id} pinned={pinned} onToggled={() => onPinToggled?.(link.id)} />
          <DeleteControl linkId={link.id} onDeleted={() => onDeleted?.(link.id)} />
        </div>
      </div>
      {!pinned && (
        <div className="h-1.5 w-full bg-black/10">
          <div
            data-testid="remaining-time-bar"
            className={`h-full ${BAR_CLASS_BY_BAND[urgencyBand]}`}
            style={{ width: `${Math.round(remainingFraction * 100)}%` }}
          />
        </div>
      )}
    </li>
  )
}
