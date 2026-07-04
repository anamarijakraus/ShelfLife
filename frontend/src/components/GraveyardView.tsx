import { LinkList } from './LinkList'
import { LinkCount } from './LinkCount'
import { EmptyGraveyardIllustration } from './EmptyGraveyardIllustration'
import { useGraveyardLinks } from '../hooks/useGraveyardLinks'

export function GraveyardView() {
  const { links, refresh } = useGraveyardLinks()

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4">
      <LinkCount links={links} label="decaying" />
      <LinkList
        links={links}
        emptyMessage="Nothing in the graveyard yet."
        emptyIllustration={<EmptyGraveyardIllustration />}
        granularity="coarse"
        openable
        onDeleted={refresh}
      />
    </div>
  )
}
