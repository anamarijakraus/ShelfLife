import { useState } from 'react'
import type { FormEvent } from 'react'
import { createLink, ApiError } from '../api/linksApi'
import type { Link } from '../types/link'

interface UrlCaptureFormProps {
  onCaptured: (link: Link) => void
}

export function UrlCaptureForm({ onCaptured }: UrlCaptureFormProps) {
  const [url, setUrl] = useState('')
  const [error, setError] = useState<string | null>(null)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()

    const trimmed = url.trim()
    if (trimmed === '') {
      return
    }

    try {
      const link = await createLink(trimmed)
      setUrl('')
      setError(null)
      onCaptured(link)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Something went wrong. Please try again.')
    }
  }

  return (
    <form onSubmit={handleSubmit} className="w-full max-w-2xl mx-auto">
      <div
        className="flex items-center gap-3 rounded-box border-2 px-4 py-3"
        style={{ borderStyle: 'dashed', borderColor: 'var(--color-capture-border)', backgroundColor: 'var(--color-capture-bg)' }}
      >
        <svg viewBox="0 0 24 24" width="26" height="26" aria-hidden="true" >
          <path d="M20.5 3.5c-6 .5-11 4-13.5 9.5-.8 1.8-1.3 3.6-1.4 5.2 1.7-.1 3.5-.6 5.3-1.4C16.4 14.3 20 9.5 20.5 3.5Z" fill="#7B4B36"></path>
          <path d="M6.2 17.8 L15 9" fill="none" stroke="#DDCBB7" stroke-width="1.1" stroke-linecap="round"></path>
          <path d="M3.5 20.5 L5.9 18.1" fill="none" stroke="#7B4B36" stroke-width="1.8" stroke-linecap="round"></path>
        </svg>
        <input
          type="text"
          value={url}
          onChange={(event) => setUrl(event.target.value)}
          placeholder="Paste a URL, hit enter — the clock starts now"
          aria-label="Paste a URL and hit enter"
          autoFocus
          className="flex-1 bg-transparent text-base-content placeholder:text-base-content/50 focus:outline-none"
        />
        <button
          type="submit"
          className="btn btn-sm rounded-full border font-mono text-xs normal-case"
          style={{
            borderColor: 'var(--color-capture-border)',
            backgroundColor: 'var(--color-badge-circle)',
            color: 'var(--color-capture-border)',
          }}
        >
          ↵ enter
        </button>
      </div>
      {error && (
        <p role="alert" className="mt-2 text-sm text-error text-center">
          {error}
        </p>
      )}
    </form>
  )
}
