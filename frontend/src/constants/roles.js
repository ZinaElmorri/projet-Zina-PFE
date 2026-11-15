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

// Anciens noms de rôle (sessions ouvertes avant la migration) → rôle actuel, comme RoleMigrationRunner.
const ANCIENS_ROLES = { ADMINISTRATEUR: 'ADMIN', GESTIONNAIRE_RH: 'EXPERT_RH', VALIDATEUR: 'MANAGER', AUDITEUR: 'MANAGER' }
export const normaliserRole = (role) => ANCIENS_ROLES[role] || role

const est = (...roles) => (role) => roles.includes(role)

export const estAdmin = est('ADMIN')
export const peutGererUtilisateurs = estAdmin
export const peutGererSocietes = estAdmin
export const peutGererXsd = est('ADMIN', 'MANAGER')
export const peutConsulterAudit = estAdmin

export const peutPreparer = est('EXPERT_RH')
export const peutSoumettre = peutPreparer
export const peutValider = est('MANAGER')
export const peutAffecter = est('MANAGER')
export const peutTraiterAnomalies = est('MANAGER', 'EXPERT_RH')
// Données et actions métier de paie : Manager et Expert RH uniquement (miroir de Roles.METIER).
export const peutVoirTransactions = est('EXPERT_RH', 'MANAGER')
/** Résultat d'un fichier : XML, IBAN, détail par salarié. L'administrateur n'a que la supervision de l'historique. */
export const peutVoirResultat = est('EXPERT_RH', 'MANAGER')
// Pilotage : vue opérationnelle pour Expert RH et Manager, vue globale (agrégats) pour l'administrateur.
export const peutVoirDashboard = est('EXPERT_RH', 'MANAGER', 'ADMIN')
export const peutVoirKpi = est('EXPERT_RH', 'MANAGER', 'ADMIN')
/** Les montants globaux sont affichés au Manager et à l'administrateur ; l'Expert RH a une version limitée. */
export const peutVoirKpiComplet = est('MANAGER', 'ADMIN')
/** Page d'accueil des rôles sans tableau de bord. */
export const PAGE_ACCUEIL_ADMIN = '/users'
