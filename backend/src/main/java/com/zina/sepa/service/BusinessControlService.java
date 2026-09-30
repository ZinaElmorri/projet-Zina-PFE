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
                erreurs.add(ref + " IBAN invalide : " + t.getIban());
            }
            if (t.getBic() != null && !t.getBic().isBlank() && !bicValide(t.getBic())) {
                erreurs.add(ref + " BIC invalide : " + t.getBic());
            }
            if (t.getMontant() == null || t.getMontant().compareTo(BigDecimal.ZERO) <= 0) {
                erreurs.add(ref + " Salaire négatif ou nul");
            }
            if (t.getIban() == null || t.getIban().isBlank()) {
                erreurs.add(ref + " Employé sans compte bancaire");
            }

            // Détection de doublon (même bénéficiaire + IBAN + montant)
            String signature = t.getIban() + "|" + t.getMontant();
            if (!signatures.add(signature)) {
                avertissements.add(ref + " Doublon de paiement potentiel");
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
        for (char c : reordered.toCharArray()) {
            numeric.append(Character.isLetter(c) ? (c - 'A' + 10) : c);
        }

        java.math.BigInteger value = new java.math.BigInteger(numeric.toString());
        return value.mod(java.math.BigInteger.valueOf(97)).intValue() == 1;
    }

    /** Validation format BIC/SWIFT (ISO 9362). */
    public boolean bicValide(String bic) {
        return bic != null && bic.toUpperCase().matches("[A-Z]{6}[A-Z0-9]{2}([A-Z0-9]{3})?");
    }
}
