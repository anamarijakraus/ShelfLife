export function PinnedBadge() {
  return (
    <span className="inline-flex items-center gap-2 whitespace-nowrap rounded-full py-0.5 pl-0.5 pr-3 text-sm font-medium text-primary-content bg-secondary">
      <span className="flex h-7 w-7 flex-shrink-0 items-center justify-center rounded-full border border-base-content/30 bg-[var(--color-badge-circle)]">
        <svg viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">
          <circle cx="12" cy="8" r="4" fill="#AD6B4B" />
          <line x1="12" y1="12" x2="12" y2="20" stroke="#AD6B4B" strokeWidth="1.5" strokeLinecap="round" />
        </svg>
      </span>
      Pinned
    </span>
  )
}
