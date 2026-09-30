package com.zina.sepa.ai.dto;

import java.util.List;

/**
 * Résultat de l'analyse IA côté backend.
 *
 * @param disponible   false si l'IA est désactivée ou n'a pas répondu
 * @param modelVersion version du modèle ayant produit les scores (null si indisponible)
 * @param resultats    résultats par paiement (vide si indisponible)
 */
public record AnalyseResult(
        boolean disponible,
        String modelVersion,
        List<ResultatDto> resultats
) {}
