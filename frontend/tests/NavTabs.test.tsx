import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { NavTabs } from '../src/components/NavTabs'

describe('NavTabs', () => {
  it('renders all three tabs', () => {
    render(<NavTabs active="active" onSelect={() => {}} />)

    expect(screen.getByRole('tab', { name: 'On the shelf' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Graveyard' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'Favorites' })).toBeInTheDocument()
  })

  it('renders the Favorites tab after Graveyard, in the order Active, Graveyard, Favorites', () => {
    render(<NavTabs active="active" onSelect={() => {}} />)

    const tabs = screen.getAllByRole('tab')
    expect(tabs.map((tab) => tab.textContent)).toEqual(['On the shelf', 'Graveyard', 'Favorites'])
  })

  it('visually indicates when the Favorites tab is currently selected', () => {
    render(<NavTabs active="favorites" onSelect={() => {}} />)

    expect(screen.getByRole('tab', { name: 'Favorites' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'On the shelf' })).toHaveAttribute('aria-selected', 'false')
    expect(screen.getByRole('tab', { name: 'Graveyard' })).toHaveAttribute('aria-selected', 'false')
  })

  it('visually indicates which tab is currently selected', () => {
    render(<NavTabs active="graveyard" onSelect={() => {}} />)

    expect(screen.getByRole('tab', { name: 'Graveyard' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tab', { name: 'On the shelf' })).toHaveAttribute('aria-selected', 'false')
  })

  it('calls onSelect with the clicked tab', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(<NavTabs active="active" onSelect={onSelect} />)

    await user.click(screen.getByRole('tab', { name: 'Graveyard' }))

    expect(onSelect).toHaveBeenCalledWith('graveyard')
  })
})
