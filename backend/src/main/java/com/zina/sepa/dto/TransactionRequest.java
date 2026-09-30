package com.zina.sepa.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record TransactionRequest(
        @NotBlank String employeId,
        @NotBlank String nomBeneficiaire,
        @NotBlank String iban,
        String bic,
        @NotNull @DecimalMin("0.01") BigDecimal montant,
        String devise,
        String libelle
) {}
