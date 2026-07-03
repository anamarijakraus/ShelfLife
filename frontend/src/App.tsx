import { UrlCaptureForm } from './components/UrlCaptureForm'
import { LinkList } from './components/LinkList'
import { ActiveCount } from './components/ActiveCount'
import { useActiveLinks } from './hooks/useActiveLinks'

function App() {
  const { links, refresh } = useActiveLinks()

  return (
    <div className="min-h-screen flex flex-col items-center px-4 py-16 gap-8">
      <h1 className="text-sm font-medium tracking-wide text-base-content/50 uppercase">
        ShelfLife
      </h1>

      <UrlCaptureForm onCaptured={refresh} />

      <div className="w-full max-w-2xl flex flex-col gap-4">
        <ActiveCount links={links} />
        <LinkList links={links} />
      </div>
    </div>
  )
}

export default App
