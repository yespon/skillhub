export function canAccessRoute(userRoles: readonly string[] | undefined, requiredRoles: readonly string[]) {
  if (!userRoles || userRoles.length === 0) {
    return false
  }

  return requiredRoles.some((role) => userRoles.includes(role))
}

export function shouldNavigateBackOnForbidden(historyLength: number) {
  return historyLength > 1
}
