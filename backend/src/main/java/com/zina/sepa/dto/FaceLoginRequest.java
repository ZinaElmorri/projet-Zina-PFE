package com.zina.sepa.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FaceLoginRequest(
        @NotBlank String username,
        @NotBlank @Size(max = 6_000_000) String image
) {}
