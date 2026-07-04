import type { Link } from '../types/link'

interface LinkCountProps {
  links: Link[]
  label?: string
}

export function LinkCount({ links, label = 'on the shelf' }: LinkCountProps) {
  return (
    <div className="flex items-center gap-4 text-xs uppercase tracking-widest text-base-content/50">
      <span aria-hidden="true" className="h-px flex-1 bg-base-content/20" />
      <p className="whitespace-nowrap font-mono">
        {links.length} {links.length === 1 ? 'link' : 'links'} {label}
      </p>
      <span aria-hidden="true" className="h-px flex-1 bg-base-content/20" />
    </div>
  )
}
