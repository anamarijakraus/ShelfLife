import { UrlCaptureForm } from './UrlCaptureForm'
import { LinkList } from './LinkList'
import { LinkCount } from './LinkCount'
import { EmptyActiveIllustration } from './EmptyActiveIllustration'
import { useActiveLinks } from '../hooks/useActiveLinks'

export function ActiveView() {
  const { links, refresh } = useActiveLinks()

  return (
    <>
      <UrlCaptureForm onCaptured={refresh} />
      <div className="w-full max-w-2xl flex flex-col gap-4">
        <LinkCount links={links} />
        <LinkList links={links} onDeleted={refresh} emptyIllustration={<EmptyActiveIllustration />} />
      </div>
    </>
  )
}
