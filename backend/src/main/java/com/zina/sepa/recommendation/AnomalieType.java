package com.zina.sepa.recommendation;

/**
 * Types d'anomalies reconnus par le moteur de recommandation.
 * Repris des contrôles déjà présents (règles métier RH, validation XSD, IA).
 */
public final class AnomalieType {

    public static final String IBAN_INVALIDE = "IBAN_INVALIDE";
    public static final String BIC_INVALIDE = "BIC_INVALIDE";
    public static final String DOUBLON_PAIEMENT = "DOUBLON_PAIEMENT";
    public static final String IBAN_PARTAGE = "IBAN_PARTAGE";
    public static final String MONTANT_INVALIDE = "MONTANT_INVALIDE";
    public static final String NET_SUPERIEUR_BRUT = "NET_SUPERIEUR_BRUT";
    public static final String MONTANT_SUSPECT = "MONTANT_SUSPECT";
    public static final String ERREUR_XSD = "ERREUR_XSD";
    public static final String CTRL_SUM_INCOHERENT = "CTRL_SUM_INCOHERENT";

    private AnomalieType() {
    }
}
