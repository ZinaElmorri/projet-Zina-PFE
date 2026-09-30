package com.zina.sepa.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Résultat par paiement renvoyé par le module IA. */
public record ResultatDto(
        Long ficheId,
        double score,
        @JsonProperty("isAnomaly") boolean isAnomaly,
        List<String> raisons
) {}
