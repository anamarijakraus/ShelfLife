import { useEffect, useRef, useState } from 'react'
import { deleteLink } from '../api/linksApi'

interface DeleteControlProps {
  linkId: number
  onDeleted: () => void
}

const ARM_TIMEOUT_MS = 3000

export function DeleteControl({ linkId, onDeleted }: DeleteControlProps) {
  const [armed, setArmed] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!armed) {
      return
    }

    const timeoutId = window.setTimeout(() => setArmed(false), ARM_TIMEOUT_MS)

    function handleOutsidePointerDown(event: MouseEvent) {
      if (containerRef.current && !containerRef.current.contains(event.target as Node)) {
        setArmed(false)
      }
    }
    document.addEventListener('mousedown', handleOutsidePointerDown)

    return () => {
      window.clearTimeout(timeoutId)
      document.removeEventListener('mousedown', handleOutsidePointerDown)
    }
  }, [armed])

  async function handleClick() {
    if (!armed) {
      setArmed(true)
      return
    }

    setArmed(false)
    try {
      await deleteLink(linkId)
      onDeleted()
    } catch {
      // Best-effort, consistent with this app's existing transient-failure handling:
      // leave the card as-is rather than surfacing an error state for a delete retry.
    }
  }

  return (
    <div ref={containerRef} className="inline-flex items-center gap-1">
      {armed && <span className="text-xs text-error">Confirm?</span>}
      <button
        type="button"
        onClick={handleClick}
        aria-label={armed ? 'Confirm delete' : 'Delete link'}
        className={`btn btn-ghost btn-xs btn-circle ${armed ? 'text-error' : 'text-base-content'}`}
      >
        {/*<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true" className="h-4 w-4">*/}
        {/*  <rect x="10" y="2" width="6" height="2.6" rx="1" />*/}
        {/*  <rect x="5" y="4.7" width="16" height="2.6" rx="1" />*/}
        {/*  <rect x="6.3" y="8" width="4.2" height="13" rx="2" />*/}
        {/*  <rect x="10.9" y="8" width="4.2" height="13" rx="2" />*/}
        {/*  <rect x="15.5" y="8" width="4.2" height="13" rx="2" />*/}
        {/*  <rect x="5.5" y="20.4" width="15" height="2.6" rx="1" />*/}
        {/*</svg>*/}
        <svg viewBox="0 0 20 22" width="22" height="24" aria-hidden="true" >
          <path d="M8 .8h4a1 1 0 0 1 1 1V3H7V1.8a1 1 0 0 1 1-1Z" fill="#7B4B36"></path>
          <rect x="2" y="3.2" width="16" height="2.6" rx="1.3" fill="#7B4B36"></rect>
          <path d="M3.5 7.2h13l-1 12.6a1.6 1.6 0 0 1-1.6 1.4H6.1a1.6 1.6 0 0 1-1.6-1.4Z" fill="#7B4B36"></path>
          <path d="M7 9.5v9M10 9.5v9M13 9.5v9" fill="none" stroke="#DDCBB7" stroke-width="1.2" stroke-linecap="round"></path>
        </svg>
      </button>
    </div>
  )
}
