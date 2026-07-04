import { describe, it, expect, vi, afterEach } from 'vitest'
import { render, screen, fireEvent, waitFor, act } from '@testing-library/react'
import { DeleteControl } from '../src/components/DeleteControl'
import * as linksApi from '../src/api/linksApi'

describe('DeleteControl', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('does not delete on a single activation, only arms the confirm state', () => {
    const onDeleted = vi.fn()
    const deleteSpy = vi.spyOn(linksApi, 'deleteLink').mockResolvedValue(undefined)

    render(<DeleteControl linkId={1} onDeleted={onDeleted} />)
    fireEvent.click(screen.getByRole('button', { name: 'Delete link' }))

    expect(deleteSpy).not.toHaveBeenCalled()
    expect(onDeleted).not.toHaveBeenCalled()
    expect(screen.getByRole('button', { name: 'Confirm delete' })).toBeInTheDocument()
  })

  it('deletes and calls onDeleted when confirmed with a second activation', async () => {
    const onDeleted = vi.fn()
    const deleteSpy = vi.spyOn(linksApi, 'deleteLink').mockResolvedValue(undefined)

    render(<DeleteControl linkId={42} onDeleted={onDeleted} />)
    fireEvent.click(screen.getByRole('button', { name: 'Delete link' }))
    fireEvent.click(screen.getByRole('button', { name: 'Confirm delete' }))

    await waitFor(() => expect(deleteSpy).toHaveBeenCalledWith(42))
    await waitFor(() => expect(onDeleted).toHaveBeenCalled())
  })

  it('auto-cancels the armed state after ~3 seconds without deleting', () => {
    vi.useFakeTimers()
    const onDeleted = vi.fn()
    const deleteSpy = vi.spyOn(linksApi, 'deleteLink').mockResolvedValue(undefined)

    render(<DeleteControl linkId={1} onDeleted={onDeleted} />)
    fireEvent.click(screen.getByRole('button', { name: 'Delete link' }))
    expect(screen.getByRole('button', { name: 'Confirm delete' })).toBeInTheDocument()

    act(() => {
      vi.advanceTimersByTime(3000)
    })

    expect(screen.getByRole('button', { name: 'Delete link' })).toBeInTheDocument()
    expect(deleteSpy).not.toHaveBeenCalled()
    expect(onDeleted).not.toHaveBeenCalled()
  })

  it('cancels the armed state when clicking outside the control, without deleting', () => {
    const onDeleted = vi.fn()
    const deleteSpy = vi.spyOn(linksApi, 'deleteLink').mockResolvedValue(undefined)

    render(
      <div>
        <div data-testid="outside">Outside</div>
        <DeleteControl linkId={1} onDeleted={onDeleted} />
      </div>,
    )
    fireEvent.click(screen.getByRole('button', { name: 'Delete link' }))
    expect(screen.getByRole('button', { name: 'Confirm delete' })).toBeInTheDocument()

    fireEvent.mouseDown(screen.getByTestId('outside'))

    expect(screen.getByRole('button', { name: 'Delete link' })).toBeInTheDocument()
    expect(deleteSpy).not.toHaveBeenCalled()
    expect(onDeleted).not.toHaveBeenCalled()
  })
})
