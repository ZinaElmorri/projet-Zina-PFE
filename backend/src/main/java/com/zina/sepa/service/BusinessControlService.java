package com.zina.sepa.service;

import com.zina.sepa.dto.ValidationResult;
import com.zina.sepa.domain.entity.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Contrôles métier RH exécutés avant la génération du fichier SEPA.
 */
@Service
public class BusinessControlService {

    /** Valide une liste de transactions et retourne les erreurs / avertissements. */
    public ValidationResult controle(List<Transaction> transactions) {
        List<String> erreurs = new ArrayList<>();
        List<String> avertissements = new ArrayList<>();
        Set<String> signatures = new HashSet<>();

        for (Transaction t : transactions) {
            String ref = "[emp %s]".formatted(t.getEmployeId());

            if (t.getIban() == null || !ibanValide(t.getIban())) {
                if (t.getIban() == null || t.getIban().isBlank()) {
                    erreurs.add(ref + " IBAN du bénéficiaire manquant. Renseignez l'IBAN dans la fiche du paiement.");
                } else if (!t.getIban().replaceAll("\\s", "").toUpperCase().matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")) {
                    erreurs.add(ref + " IBAN invalide : format incorrect. Vérifiez les deux lettres du pays, les deux chiffres de contrôle et le numéro de compte dans la fiche du paiement.");
                } else {
                    erreurs.add(ref + " IBAN invalide : clé de contrôle incohérente (contrôle modulo 97). Vérifiez l'IBAN du bénéficiaire à partir de son relevé bancaire et corrigez la fiche du paiement.");
                }
            }
            if (t.getBic() != null && !t.getBic().isBlank() && !bicValide(t.getBic())) {
                erreurs.add(ref + " BIC invalide : renseignez le code SWIFT de la banque du bénéficiaire avec 8 ou 11 caractères, sans espaces. Exemple : AGRIFRPP. Vérifiez ce code sur le relevé bancaire.");
            }
            if (t.getMontant() == null || t.getMontant().compareTo(BigDecimal.ZERO) <= 0) {
                erreurs.add(ref + (t.getMontant() == null ? " Montant du virement manquant." : " Montant du virement négatif ou nul.")
                        + " Renseignez un montant strictement supérieur à zéro dans la fiche du paiement.");
            }
            if (t.getIban() == null || t.getIban().isBlank()) {
                erreurs.add(ref + " Compte bancaire du bénéficiaire incomplet. Complétez l'IBAN avant de préparer le virement.");
            }

            // Détection de doublon (même bénéficiaire + IBAN + montant)
            String signature = t.getIban() + "|" + t.getMontant();
            if (!signatures.add(signature)) {
                avertissements.add(ref + " Doublon de paiement potentiel : le même IBAN et le même montant apparaissent plusieurs fois. Vérifiez les paiements sélectionnés pour éviter de payer deux fois. Cet avertissement ne rend pas le XML non conforme au XSD.");
            }
        }

        return new ValidationResult(erreurs.isEmpty(), erreurs, avertissements);
    }

    /**
     * Validation IBAN par contrôle modulo 97 (norme ISO 13616).
     */
    public boolean ibanValide(String iban) {
        if (iban == null) return false;
        String normalized = iban.replaceAll("\\s", "").toUpperCase();
        if (!normalized.matches("[A-Z0-9]{15,34}")) return false;

        String reordered = normalized.substring(4) + normalized.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char caractere : reordered.toCharArray()) {
            if (caractere >= 'A' && caractere <= 'Z') {
                numeric.append(caractere - 'A' + 10);
            } else {
                numeric.append(caractere);
            }
        }

        java.math.BigInteger value = new java.math.BigInteger(numeric.toString());
        return value.mod(java.math.BigInteger.valueOf(97)).intValue() == 1;
    }

    /** Validation format BIC/SWIFT (ISO 9362). */
    public boolean bicValide(String bic) {
        return bic != null && bic.toUpperCase().matches("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?");
    }
}
