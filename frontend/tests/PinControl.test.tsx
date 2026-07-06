import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { PinControl } from '../src/components/PinControl'
import * as linksApi from '../src/api/linksApi'

describe('PinControl', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('rendered unpinned, a click calls pinLink and then onToggled', async () => {
    const onToggled = vi.fn()
    const pinSpy = vi.spyOn(linksApi, 'pinLink').mockResolvedValue(undefined)
    const unpinSpy = vi.spyOn(linksApi, 'unpinLink').mockResolvedValue(undefined)

    render(<PinControl linkId={1} pinned={false} onToggled={onToggled} />)
    fireEvent.click(screen.getByRole('button', { name: 'Pin link' }))

    await waitFor(() => expect(pinSpy).toHaveBeenCalledWith(1))
    await waitFor(() => expect(onToggled).toHaveBeenCalled())
    expect(unpinSpy).not.toHaveBeenCalled()
  })

  it('rendered pinned, a click calls unpinLink and then onToggled', async () => {
    const onToggled = vi.fn()
    const pinSpy = vi.spyOn(linksApi, 'pinLink').mockResolvedValue(undefined)
    const unpinSpy = vi.spyOn(linksApi, 'unpinLink').mockResolvedValue(undefined)

    render(<PinControl linkId={42} pinned={true} onToggled={onToggled} />)
    fireEvent.click(screen.getByRole('button', { name: 'Unpin link' }))

    await waitFor(() => expect(unpinSpy).toHaveBeenCalledWith(42))
    await waitFor(() => expect(onToggled).toHaveBeenCalled())
    expect(pinSpy).not.toHaveBeenCalled()
  })

  it("the control's accessible name differs between the two states", () => {
    const { rerender } = render(<PinControl linkId={1} pinned={false} onToggled={() => {}} />)
    expect(screen.getByRole('button', { name: 'Pin link' })).toBeInTheDocument()

    rerender(<PinControl linkId={1} pinned={true} onToggled={() => {}} />)
    expect(screen.getByRole('button', { name: 'Unpin link' })).toBeInTheDocument()
  })
})
