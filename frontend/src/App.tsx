import { useState } from 'react'
import { NavTabs } from './components/NavTabs'
import type { View } from './components/NavTabs'
import { ActiveView } from './components/ActiveView'
import { GraveyardView } from './components/GraveyardView'

function App() {
  const [view, setView] = useState<View>('active')

  return (
    <div className="min-h-screen flex flex-col items-center px-4 py-16 gap-8">
      <h1 className="text-sm font-medium tracking-wide text-base-content/50 uppercase">
        ShelfLife
      </h1>

      <NavTabs active={view} onSelect={setView} />

      {view === 'active' ? <ActiveView /> : <GraveyardView />}
    </div>
  )
}

export default App
