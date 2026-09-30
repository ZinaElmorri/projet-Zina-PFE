package com.zina.sepa.ai.dto;

import java.util.List;

/** Réponse de POST /anomalies/detect. */
public record MontantSuspectResponse(
        String modelVersion,
        List<ResultatDto> resultats
) {}
