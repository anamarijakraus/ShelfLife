import type { Link } from '../types/link'
import { LinkListItem } from './LinkListItem'

interface LinkListProps {
  links: Link[]
  emptyMessage?: string
  granularity?: 'fine' | 'coarse'
  openable?: boolean
}

export function LinkList({
  links,
  emptyMessage = 'Nothing saved yet — paste a URL above to get started.',
  granularity = 'fine',
  openable = false,
}: LinkListProps) {
  if (links.length === 0) {
    return <p className="text-center text-base-content/60">{emptyMessage}</p>
  }

  return (
    <ul className="w-full max-w-2xl mx-auto flex flex-col gap-2">
      {links.map((link) => (
        <LinkListItem key={link.id} link={link} granularity={granularity} openable={openable} />
      ))}
    </ul>
  )
}
