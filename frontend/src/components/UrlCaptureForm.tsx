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
      <input
        type="text"
        value={url}
        onChange={(event) => setUrl(event.target.value)}
        placeholder="Paste a URL and hit enter…"
        aria-label="Paste a URL and hit enter"
        autoFocus
        className="input input-lg w-full text-center"
      />
      {error && (
        <p role="alert" className="mt-2 text-sm text-error text-center">
          {error}
        </p>
      )}
    </form>
  )
}
