export type MySkillFilter = 'ALL' | 'PENDING_REVIEW' | 'PUBLISHED' | 'REJECTED' | 'ARCHIVED' | 'HIDDEN'

export function getMySkillFilters(isSuperAdmin: boolean): MySkillFilter[] {
  if (isSuperAdmin) {
    return ['ALL', 'PUBLISHED', 'HIDDEN', 'ARCHIVED']
  }
  return ['ALL', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED']
}

