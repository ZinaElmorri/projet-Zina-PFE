package com.zina.sepa.domain.enums;

import java.util.EnumSet;
import java.util.Set;

/**
 * Rôles applicatifs. Les anciens rôles sont conservés uniquement pour que les lignes existantes en base
 * se chargent jusqu'à leur migration (voir RoleMigrationRunner) ; ils ne sont plus attribuables.
 */
public enum Role {
    ADMIN,
    MANAGER,
    EXPERT_RH,

    @Deprecated ADMINISTRATEUR,
    @Deprecated GESTIONNAIRE_RH,
    @Deprecated VALIDATEUR,
    @Deprecated AUDITEUR;

    private static final Set<Role> ATTRIBUABLES = EnumSet.of(ADMIN, MANAGER, EXPERT_RH);

    public boolean estAttribuable() {
        return ATTRIBUABLES.contains(this);
    }
}
