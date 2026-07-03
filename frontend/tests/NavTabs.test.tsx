import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { NavTabs } from '../src/components/NavTabs'

describe('NavTabs', () => {
  it('renders both tabs', () => {
    render(<NavTabs active="active" onSelect={() => {}} />)

    expect(screen.getByRole('tab', { name: 'Active' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Graveyard' })).toBeInTheDocument()
  })

  it('visually indicates which tab is currently selected', () => {
    render(<NavTabs active="graveyard" onSelect={() => {}} />)

    expect(screen.getByRole('tab', { name: 'Graveyard' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'Active' })).toHaveAttribute('aria-selected', 'false')
  })

  it('calls onSelect with the clicked tab', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(<NavTabs active="active" onSelect={onSelect} />)

    await user.click(screen.getByRole('tab', { name: 'Graveyard' }))

    expect(onSelect).toHaveBeenCalledWith('graveyard')
  })
})
