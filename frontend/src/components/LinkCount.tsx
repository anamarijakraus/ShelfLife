import type { Link } from '../types/link'

interface LinkCountProps {
  links: Link[]
  label?: string
}

export function LinkCount({ links, label = 'saved' }: LinkCountProps) {
  return (
    <p className="text-center text-sm text-base-content/60">
      {links.length} {links.length === 1 ? 'link' : 'links'} {label}
    </p>
  )
}
