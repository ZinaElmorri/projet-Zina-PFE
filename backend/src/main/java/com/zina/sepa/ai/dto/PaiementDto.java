package com.zina.sepa.ai.dto;

import java.math.BigDecimal;
import java.util.List;

/** Un paiement transmis au module IA. */
public record PaiementDto(
        Long ficheId,
        String salarieId,
        String clientId,
        BigDecimal montant,
        String iban,
        boolean ibanChange,
        List<BigDecimal> historique
) {}
