package com.zina.sepa.ai.dto;

/** État de santé du module IA (GET /model/health). */
public record AiHealth(
        String status,
        String modelVersion
) {}
