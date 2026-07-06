import { LinkList } from './LinkList'
import { LinkCount } from './LinkCount'
import { EmptyFavoritesIllustration } from './EmptyFavoritesIllustration'
import { useFavoriteLinks } from '../hooks/useFavoriteLinks'

export function FavoritesView() {
  const { links, refresh } = useFavoriteLinks()

  return (
    <div className="w-full max-w-2xl flex flex-col gap-4">
      <LinkCount links={links} label="pinned" />
      <LinkList
        links={links}
        emptyMessage="Nothing pinned yet."
        emptyIllustration={<EmptyFavoritesIllustration />}
        openable
        pinned
        onDeleted={refresh}
        onPinToggled={refresh}
      />
    </div>
  )
}
