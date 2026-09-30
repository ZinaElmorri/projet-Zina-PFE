package com.zina.sepa.recommendation.dto;

/** Requête de changement de statut d'une anomalie. */
public record ChangementStatutRequest(
        String statut,
        String commentaire
) {}
