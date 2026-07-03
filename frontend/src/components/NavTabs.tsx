export type View = 'active' | 'graveyard'

interface NavTabsProps {
  active: View
  onSelect: (view: View) => void
}

export function NavTabs({ active, onSelect }: NavTabsProps) {
  return (
    <div role="tablist" className="tabs tabs-boxed">
      <button
        role="tab"
        type="button"
        aria-selected={active === 'active'}
        className={`tab ${active === 'active' ? 'tab-active' : ''}`}
        onClick={() => onSelect('active')}
      >
        Active
      </button>
      <button
        role="tab"
        type="button"
        aria-selected={active === 'graveyard'}
        className={`tab ${active === 'graveyard' ? 'tab-active' : ''}`}
        onClick={() => onSelect('graveyard')}
      >
        Graveyard
      </button>
    </div>
  )
}
