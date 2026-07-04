export type UrgencyBand = 'fresh' | 'turning' | 'wilted'

interface HourglassMotifProps {
  urgency: UrgencyBand
}

// Color reinforces urgency, but the shape itself also carries the signal: the "sand" visibly
// pools from the top chamber toward the bottom one as time runs out, so the cue survives even
// viewed without relying on color perception (FR-012a) — a literal hourglass metaphor.
const FRAME_COLOR_BY_BAND: Record<UrgencyBand, string> = {
  fresh: '#7B4B36',
  turning: '#9A5A3F',
  wilted: '#AD6B4B',
}

const SAND_PATH_BY_BAND: Record<UrgencyBand, string> = {
  fresh: 'M8 4.8L16 4.8L12 11Z',
  turning: 'M9 6L15 6L12 10ZM9 18L15 18L12 14Z',
  wilted: 'M8 19.2L16 19.2L12 13Z',
}

export function HourglassMotif({ urgency }: HourglassMotifProps) {
  return (
    <svg
      data-testid="hourglass-motif"
      data-urgency={urgency}
      viewBox="0 0 24 24"
      aria-hidden="true"
      className="h-4 w-4 flex-shrink-0"
    >
      <path fill={FRAME_COLOR_BY_BAND[urgency]} d="M6 3L18 3L18 5.4L12 12L18 18.6L18 21L6 21L6 18.6L12 12L6 5.4Z" />
      <path fill="var(--color-badge-circle)" d={SAND_PATH_BY_BAND[urgency]} />
    </svg>
  )
}


