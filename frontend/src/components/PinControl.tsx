import { pinLink, unpinLink } from '../api/linksApi'

interface PinControlProps {
  linkId: number
  pinned: boolean
  onToggled?: () => void
}

export function PinControl({ linkId, pinned, onToggled }: PinControlProps) {
  async function handleClick() {
    try {
      if (pinned) {
        await unpinLink(linkId)
      } else {
        await pinLink(linkId)
      }
      onToggled?.()
    } catch {
      // Best-effort, consistent with DeleteControl's existing transient-failure handling:
      // leave the card as-is rather than surfacing an error state for a retry.
    }
  }

  return (
    <button
      type="button"
      onClick={handleClick}
      aria-label={pinned ? 'Unpin link' : 'Pin link'}
      aria-pressed={pinned}
      className={`btn btn-ghost btn-xs btn-circle ${pinned ? 'text-accent' : 'text-base-content/60'}`}
    >
      <svg viewBox="0 0 24 24" width="18" height="18" aria-hidden="true">
        <circle
          cx="12"
          cy="8"
          r="4"
          fill={pinned ? 'currentColor' : 'none'}
          stroke="currentColor"
          strokeWidth="1.5"
        />
        <line x1="12" y1="12" x2="12" y2="20" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
      </svg>
    </button>
  )
}
