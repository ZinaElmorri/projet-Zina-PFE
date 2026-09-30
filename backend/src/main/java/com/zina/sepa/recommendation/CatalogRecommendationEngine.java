package com.zina.sepa.recommendation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Recommandations basées sur un catalogue par type d'anomalie.
 * Utilise les vraies valeurs de l'anomalie (matricule, IBAN masqué, montant, période…).
 */
@Component
public class CatalogRecommendationEngine implements RecommendationEngine {

    private static final Logger log = LoggerFactory.getLogger(CatalogRecommendationEngine.class);

    private static final String GENERIQUE =
            "Vérifier les données de la fiche de paie concernée, corriger l'erreur, puis relancer le traitement.";

    @Override
    public String recommander(Anomalie a) {
        if (a == null || a.getType() == null) {
            return GENERIQUE;
        }
        try {
            return construire(a);
        } catch (Exception e) {
            // Ne jamais bloquer la détection : repli sur une recommandation générique.
            log.warn("Recommandation indisponible pour le type {} : {}", a.getType(), e.getMessage());
            return GENERIQUE;
        }
    }

    private String construire(Anomalie a) {
        String matricule = valeur(a.getMatricule(), "inconnu");
        String ibanMasque = IbanUtil.masquer(a.getIban());
        String montant = montant(a.getMontant());
        String periode = valeur(a.getPeriode(), "la période courante");

        switch (a.getType()) {
            case AnomalieType.IBAN_INVALIDE:
                return "IBAN " + ibanMasque + " du salarié " + matricule
                        + " invalide (clé modulo 97). Demander un RIB à jour au salarié, "
                        + "corriger l'IBAN dans la fiche salarié, puis relancer la génération.";
            case AnomalieType.BIC_INVALIDE:
                return "BIC " + valeur(a.getBic(), "?") + " invalide : il doit contenir 8 ou 11 caractères. "
                        + "Corriger le BIC dans la fiche salarié.";
            case AnomalieType.DOUBLON_PAIEMENT:
                return "Le salarié " + matricule + " est payé " + nb(a) + " fois sur la période " + periode
                        + ". Supprimer la fiche de paie en double (id " + valeur(a.getTransactionId(), "?")
                        + ") avant de relancer la génération.";
            case AnomalieType.IBAN_PARTAGE:
                return "L'IBAN " + ibanMasque + " est utilisé par " + nb(a) + " salariés ("
                        + valeur(a.getMatricules(), matricule) + "). Vérifier s'il s'agit d'un compte joint "
                        + "légitime ; sinon, corriger l'IBAN du salarié concerné.";
            case AnomalieType.MONTANT_INVALIDE:
                return "Montant de " + montant + " € pour " + matricule
                        + " : un virement de salaire doit être strictement positif. "
                        + "Corriger le montant ou exclure la fiche du lot.";
            case AnomalieType.NET_SUPERIEUR_BRUT:
                return "Le net (" + montant(a.getNet()) + " €) est supérieur au brut (" + montant(a.getBrut())
                        + " €) pour " + matricule + " : vérifier le calcul des cotisations, puis corriger la fiche.";
            case AnomalieType.MONTANT_SUSPECT:
                return "Montant de " + montant + " € (" + valeur(a.getRaisons(), a.getDetails())
                        + "). Vérifier les primes, rappels ou régularisations ; si le montant est justifié, "
                        + "ignorer l'anomalie avec un commentaire, sinon corriger le montant.";
            case AnomalieType.ERREUR_XSD:
                return "Élément " + valeur(a.getElementXsd(), "?") + " invalide ("
                        + valeur(a.getMessageXsd(), a.getDetails()) + "). Nettoyer le champ "
                        + "(caractères autorisés SEPA : lettres latines sans accent, chiffres, / - ? : ( ) . , ' + espace ; "
                        + "respecter la longueur maximale), puis régénérer le fichier.";
            case AnomalieType.CTRL_SUM_INCOHERENT:
                return "Le nombre de transactions ou la somme de contrôle ne correspond pas aux virements. "
                        + "Régénérer le lot après correction des fiches.";
            default:
                return GENERIQUE;
        }
    }

    private String nb(Anomalie a) {
        return a.getNb() != null ? String.valueOf(a.getNb()) : "plusieurs";
    }

    private String montant(BigDecimal m) {
        return m != null ? m.toPlainString() : "?";
    }

    private String valeur(Object v, String defaut) {
        if (v == null) {
            return defaut;
        }
        String s = String.valueOf(v);
        return s.isBlank() ? defaut : s;
    }
}
