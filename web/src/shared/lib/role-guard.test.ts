import { describe, expect, it } from 'vitest'
import { canAccessRoute, shouldNavigateBackOnForbidden } from './role-guard'

describe('canAccessRoute', () => {
  it('returns true when the user has one of the required roles', () => {
    expect(canAccessRoute(['USER', 'SKILL_ADMIN'], ['SKILL_ADMIN', 'SUPER_ADMIN'])).toBe(true)
  })

  it('returns false when the user does not have any required roles', () => {
    expect(canAccessRoute(['USER'], ['SKILL_ADMIN', 'SUPER_ADMIN'])).toBe(false)
  })
})

describe('shouldNavigateBackOnForbidden', () => {
  it('returns true when there is browser history to go back to', () => {
    expect(shouldNavigateBackOnForbidden(2)).toBe(true)
  })

  it('returns false when the current page is the first history entry', () => {
    expect(shouldNavigateBackOnForbidden(1)).toBe(false)
  })
})
