package com.zina.sepa.security;

/**
 * Expressions d'autorisation centralisées pour {@code @PreAuthorize}.
 *
 * <ul>
 *   <li>ADMIN : utilisateurs, sociétés, schémas XSD, audit ; ne prépare ni ne valide de virements ;</li>
 *   <li>MANAGER : affecte les sociétés, valide ou rejette les fichiers soumis ;</li>
 *   <li>EXPERT_RH : prépare, contrôle, génère et soumet les fichiers de ses sociétés.</li>
 * </ul>
 */
public final class Roles {

    public static final String ADMIN = "hasRole('ADMIN')";
    public static final String MANAGER = "hasRole('MANAGER')";
    public static final String EXPERT_RH = "hasRole('EXPERT_RH')";

    /** Préparer, générer et soumettre des virements. */
    public static final String PREPARATION = "hasRole('EXPERT_RH')";
    /** Valider ou rejeter les fichiers soumis. */
    public static final String VALIDATION = "hasRole('MANAGER')";
    /** Affecter les sociétés aux Experts RH. */
    public static final String AFFECTATION = "hasRole('MANAGER')";
    /** Lecture des données métier. */
    public static final String CONSULTATION = "hasAnyRole('EXPERT_RH','MANAGER','ADMIN')";
    /** Import, modification et suppression des schémas XSD. */
    public static final String XSD = "hasAnyRole('ADMIN','MANAGER')";

    private Roles() {
    }
}
