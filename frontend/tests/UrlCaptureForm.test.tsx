import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { UrlCaptureForm } from '../src/components/UrlCaptureForm'
import * as linksApi from '../src/api/linksApi'

describe('UrlCaptureForm', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('submits a valid URL and clears the input', async () => {
    const createdLink = {
      id: 1,
      url: 'https://example.com',
      title: 'https://example.com',
      faviconUrl: null,
      savedAt: '2026-07-02T00:00:00Z',
      expiresAt: '2026-07-09T00:00:00Z',
    }
    vi.spyOn(linksApi, 'createLink').mockResolvedValue(createdLink)
    const onCaptured = vi.fn()

    render(<UrlCaptureForm onCaptured={onCaptured} />)
    const input = screen.getByRole('textbox')

    await userEvent.type(input, 'https://example.com{enter}')

    expect(linksApi.createLink).toHaveBeenCalledWith('https://example.com')
    expect(onCaptured).toHaveBeenCalledWith(createdLink)
    expect(input).toHaveValue('')
  })

  it('does nothing when submitted empty', async () => {
    vi.spyOn(linksApi, 'createLink')
    const onCaptured = vi.fn()

    render(<UrlCaptureForm onCaptured={onCaptured} />)
    await userEvent.type(screen.getByRole('textbox'), '{enter}')

    expect(linksApi.createLink).not.toHaveBeenCalled()
    expect(onCaptured).not.toHaveBeenCalled()
  })

  it('shows a non-blocking inline error for an invalid URL', async () => {
    vi.spyOn(linksApi, 'createLink').mockRejectedValue(
      new linksApi.ApiError('The submitted value is not a valid URL.'),
    )

    render(<UrlCaptureForm onCaptured={vi.fn()} />)
    await userEvent.type(screen.getByRole('textbox'), 'not a url!!{enter}')

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The submitted value is not a valid URL.',
    )
  })
})
