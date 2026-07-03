import type { Link } from '../types/link'

interface ActiveCountProps {
  links: Link[]
}

export function ActiveCount({ links }: ActiveCountProps) {
  return (
    <p className="text-center text-sm text-base-content/60">
      {links.length} {links.length === 1 ? 'link' : 'links'} saved
    </p>
  )
}
