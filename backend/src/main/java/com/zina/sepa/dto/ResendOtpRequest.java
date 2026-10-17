package com.zina.sepa.dto;

import jakarta.validation.constraints.NotBlank;

public record ResendOtpRequest(@NotBlank String challengeId) {}
