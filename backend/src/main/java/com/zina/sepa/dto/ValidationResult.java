package com.zina.sepa.dto;

import java.util.List;

/**
 * Résultat d'un contrôle (validation XML/XSD ou contrôle métier RH).
 */
public record ValidationResult(
        boolean valide,
        List<String> erreurs,
        List<String> avertissements
) {
    public static ValidationResult ok() {
        return new ValidationResult(true, List.of(), List.of());
    }
}
