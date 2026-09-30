package com.zina.sepa.ai.dto;

/** Réponse de POST /model/train. */
public record TrainResponse(
        String modelVersion,
        String message
) {}
