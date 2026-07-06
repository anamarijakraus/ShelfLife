import { useState } from 'react'
import { NavTabs } from './components/NavTabs'
import type { View } from './components/NavTabs'
import { ActiveView } from './components/ActiveView'
import { GraveyardView } from './components/GraveyardView'
import { FavoritesView } from './components/FavoritesView'

function App() {
  const [view, setView] = useState<View>('active')

  return (
    <div className="min-h-screen flex flex-col items-center px-4 py-16 gap-8">
      <header className="flex flex-col items-center gap-3 text-center">
        <h1 className="font-serif text-5xl leading-none">
          <span className="text-primary">Shelf</span>
          <span className="italic text-accent">life</span>
        </h1>
        <svg viewBox="0 0 160 16" aria-hidden="true" className="h-3 w-36 text-accent">
          <path
            d="M2 8c13-7 26 7 39 0s26-7 39 0 26 7 39 0 26-7 39 0"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
          />
        </svg>
        <p className="max-w-xl text-sm text-base-content/80">
          Links live <strong className="font-semibold text-base-content">7 days</strong> on the shelf, decay{' '}
          <strong className="font-semibold text-base-content">30 days</strong> in the graveyard — then they turn
          to dust.
        </p>
      </header>

      <NavTabs active={view} onSelect={setView} />

      {view === 'active' ? <ActiveView /> : view === 'graveyard' ? <GraveyardView /> : <FavoritesView />}
    </div>
  )
}

export default App
