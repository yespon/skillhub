import { describe, expect, it } from 'vitest'
import { getMySkillFilters } from './my-skill-filters'

describe('getMySkillFilters', () => {
  it('returns lifecycle filters for regular users', () => {
    expect(getMySkillFilters(false)).toEqual(['ALL', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED'])
  })

  it('adds hidden filter for super admins', () => {
    expect(getMySkillFilters(true)).toEqual(['ALL', 'PUBLISHED', 'HIDDEN', 'ARCHIVED'])
  })
})
