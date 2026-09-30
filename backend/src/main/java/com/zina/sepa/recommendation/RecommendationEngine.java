package com.zina.sepa.recommendation;

/** Fournit une recommandation de correction pour une anomalie. */
public interface RecommendationEngine {

    /**
     * Retourne les actions techniques à réaliser pour corriger l'anomalie.
     * Ne doit jamais lever d'exception : renvoie une recommandation générique
     * pour un type inconnu ou en cas d'erreur.
     */
    String recommander(Anomalie anomalie);
}
