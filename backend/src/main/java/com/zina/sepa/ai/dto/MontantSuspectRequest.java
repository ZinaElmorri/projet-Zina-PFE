package com.zina.sepa.ai.dto;

import java.util.List;

/** Requête envoyée à POST /anomalies/detect. */
public record MontantSuspectRequest(
        String periode,
        List<PaiementDto> paiements
) {}
