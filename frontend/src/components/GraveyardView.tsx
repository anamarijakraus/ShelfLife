import { LinkList } from './LinkList'
import { LinkCount } from './LinkCount'
import { useGraveyardLinks } from '../hooks/useGraveyardLinks'

export function GraveyardView() {
  const { links } = useGraveyardLinks()

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4">
      <LinkCount links={links} label="in the graveyard" />
      <LinkList links={links} emptyMessage="Nothing in the graveyard yet." granularity="coarse" openable />
    </div>
  )
}
