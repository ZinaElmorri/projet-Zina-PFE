package com.zina.sepa.recommendation.dto;

import java.math.BigDecimal;

/** Vue d'une anomalie pour l'écran de suivi. */
public record AnomalieDto(
        Long id,
        String type,
        String matricule,
        String ibanMasque,
        BigDecimal montant,
        String periode,
        String details,
        String recommandation,
        String statut,
        String commentaireCorrection,
        String corrigeePar,
        String corrigeeLe,
        Long transactionId
) {}
