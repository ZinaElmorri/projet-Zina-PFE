package com.zina.sepa.dto;

/**
 * Réponse du module IA pour une transaction analysée.
 */
public record AnomalyScore(
        Long transactionId,
        double scoreRisque,
        String niveauRisque,
        String explication
) {}
