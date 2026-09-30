package com.zina.sepa.dashboard;

/**
 * Filtres optionnels du dashboard KPI. Point d'entrée unique pour brancher
 * un futur filtrage par rôle (departementId / expertId) sans toucher au reste.
 *
 * <p>Note : {@code departementId} et {@code expertId} sont acceptés mais ignorés
 * tant que ces notions n'existent pas dans le modèle.</p>
 */
public record DashboardFilter(
        String periode,
        String clientId,
        String departementId,
        String expertId
) {}
