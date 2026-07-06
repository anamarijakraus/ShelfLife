import type { ReactNode } from 'react'
import type { Link } from '../types/link'
import { LinkListItem } from './LinkListItem'

interface LinkListProps {
  links: Link[]
  emptyMessage?: string
  emptyIllustration?: ReactNode
  granularity?: 'fine' | 'coarse'
  openable?: boolean
  pinned?: boolean
  onDeleted?: (id: number) => void
  onPinToggled?: (id: number) => void
}

export function LinkList({
  links,
  emptyMessage = 'Nothing saved yet — paste a URL above to get started.',
  emptyIllustration,
  granularity = 'fine',
  openable = false,
  pinned = false,
  onDeleted,
  onPinToggled,
}: LinkListProps) {
  if (links.length === 0) {
    return (
      <div className="flex flex-col items-center gap-3">
        {emptyIllustration}
        <p className="text-center text-base-content/60">{emptyMessage}</p>
      </div>
    )
  }

  return (
    <ul className="w-full max-w-2xl mx-auto flex flex-col gap-3">
      {links.map((link, i) => (
        <LinkListItem
          key={link.id}
          link={link}
          index={i + 1}
          granularity={granularity}
          openable={openable}
          pinned={pinned}
          onDeleted={onDeleted}
          onPinToggled={onPinToggled}
        />
      ))}
    </ul>
  )
}
