package com.zina.sepa.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Résultat par paiement renvoyé par le module IA.
 *
 * <p>{@code typeProbable} et {@code recommandation} sont facultatifs (absents d'un module IA plus ancien
 * et pour un paiement non signalé).</p>
 */
public record ResultatDto(
        Long ficheId,
        double score,
        @JsonProperty("isAnomaly") boolean isAnomaly,
        List<String> raisons,
        String typeProbable,
        String recommandation
) {
    /** Forme historique, sans type probable ni recommandation. */
    public ResultatDto(Long ficheId, double score, boolean isAnomaly, List<String> raisons) {
        this(ficheId, score, isAnomaly, raisons, null, null);
    }
}
