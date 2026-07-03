import type { Link } from '../types/link'

const BASE_URL = '/api/links'

export class ApiError extends Error {
  constructor(message: string) {
    super(message)
    this.name = 'ApiError'
  }
}

async function parseErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json()
    if (typeof body?.message === 'string') {
      return body.message
    }
  } catch {
    // response had no JSON body; fall through to generic message
  }
  return 'The submitted value is not a valid URL.'
}

export async function createLink(url: string): Promise<Link> {
  const response = await fetch(BASE_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ url }),
  })

  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response))
  }

  return (await response.json()) as Link
}

export async function fetchActiveLinks(): Promise<Link[]> {
  const response = await fetch(BASE_URL)

  if (!response.ok) {
    throw new ApiError('Failed to load active links.')
  }

  const body = (await response.json()) as { links: Link[] }
  return body.links
}

export async function fetchGraveyardLinks(): Promise<Link[]> {
  const response = await fetch(`${BASE_URL}/graveyard`)

  if (!response.ok) {
    throw new ApiError('Failed to load graveyard links.')
  }

  const body = (await response.json()) as { links: Link[] }
  return body.links
}
