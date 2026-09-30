package com.zina.sepa.recommendation.dto;

/** Compteurs retournés après une (re)détection. */
public record DetectionResume(
        int nouvelles,
        long ouvertes,
        long corrigees,
        long ignorees
) {}
