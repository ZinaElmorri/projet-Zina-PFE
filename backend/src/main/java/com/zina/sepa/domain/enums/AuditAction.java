package com.zina.sepa.domain.enums;

/**
 * Types d'actions tracées dans le journal d'audit.
 */
public enum AuditAction {
    LOGIN,
    LOGOUT,
    PAGE_VISITEE,
    XSD_IMPORTE,
    XML_GENERE,
    TRANSACTION_CREEE,
    TRANSACTION_MODIFIEE,
    TRANSACTION_APPROUVEE,
    TRANSACTION_SUPPRIMEE,
    ANOMALIE_DETECTEE,
    VALIDATION_EFFECTUEE,
    ROLES_MIGRES,
    FICHIER_SOUMIS,
    FICHIER_VALIDE,
    FICHIER_REJETE,
    AFFECTATION_MODIFIEE
}
