// Source unique des rôles et des règles de visibilité (menu, routes, actions).
// Les règles reflètent les autorisations du backend (security/Roles.java) : le backend reste l'autorité.

export const ROLES = {
  ADMIN: { label: 'Administrateur', color: 'geekblue' },
  MANAGER: { label: 'Manager', color: 'green' },
  EXPERT_RH: { label: 'Expert RH', color: 'cyan' },
}

/** Rôles attribuables à un compte (liste déroulante des formulaires). */
export const ROLES_ATTRIBUABLES = Object.entries(ROLES).map(([value, { label }]) => ({ value, label }))

export const ROLE_PAR_DEFAUT = 'EXPERT_RH'

export const libelleRole = (role) => ROLES[role]?.label || role

const est = (...roles) => (role) => roles.includes(role)

export const estAdmin = est('ADMIN')
export const peutGererUtilisateurs = estAdmin
export const peutGererSocietes = estAdmin
export const peutGererXsd = estAdmin
export const peutConsulterAudit = estAdmin

export const peutPreparer = est('EXPERT_RH')
export const peutSoumettre = peutPreparer
export const peutValider = est('MANAGER')
export const peutAffecter = est('MANAGER')
export const peutTraiterAnomalies = est('ADMIN', 'MANAGER', 'EXPERT_RH')
