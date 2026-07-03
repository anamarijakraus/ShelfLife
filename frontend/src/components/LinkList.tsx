import type { Link } from '../types/link'
import { LinkListItem } from './LinkListItem'

interface LinkListProps {
  links: Link[]
}

export function LinkList({ links }: LinkListProps) {
  if (links.length === 0) {
    return (
      <p className="text-center text-base-content/60">
        Nothing saved yet — paste a URL above to get started.
      </p>
    )
  }

  return (
    <ul className="w-full max-w-2xl mx-auto flex flex-col gap-2">
      {links.map((link) => (
        <LinkListItem key={link.id} link={link} />
      ))}
    </ul>
  )
}
