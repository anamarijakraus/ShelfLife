export type View = 'active' | 'graveyard' | 'favorites'

interface NavTabsProps {
  active: View
  onSelect: (view: View) => void
}

const BASE_PILL_CLASS = 'btn btn-sm rounded-full border-2 px-5 font-medium normal-case'
const ACTIVE_PILL_CLASS = 'border-primary bg-primary text-primary-content'
const INACTIVE_PILL_CLASS = 'border-primary bg-transparent text-primary hover:bg-primary/10'

export function NavTabs({ active, onSelect }: NavTabsProps) {
  return (
    <div role="tablist" className="flex items-center gap-3">
      <button
        role="tab"
        type="button"
        aria-selected={active === 'active'}
        className={`${BASE_PILL_CLASS} ${active === 'active' ? ACTIVE_PILL_CLASS : INACTIVE_PILL_CLASS}`}
        onClick={() => onSelect('active')}
      >
        On the shelf
      </button>
      <button
        role="tab"
        type="button"
        aria-selected={active === 'graveyard'}
        className={`${BASE_PILL_CLASS} ${active === 'graveyard' ? ACTIVE_PILL_CLASS : INACTIVE_PILL_CLASS}`}
        onClick={() => onSelect('graveyard')}
      >
        Graveyard
      </button>
      <button
        role="tab"
        type="button"
        aria-selected={active === 'favorites'}
        className={`${BASE_PILL_CLASS} ${active === 'favorites' ? ACTIVE_PILL_CLASS : INACTIVE_PILL_CLASS}`}
        onClick={() => onSelect('favorites')}
      >
        Favorites
      </button>
    </div>
  )
}
