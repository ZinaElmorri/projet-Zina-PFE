package com.zina.sepa.dto;

public record AuthResponse(
        String token,
        String username,
        String role
) {}
