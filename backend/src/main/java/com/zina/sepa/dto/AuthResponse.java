package com.zina.sepa.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Réponse d'authentification : soit un token JWT, soit un défi OTP à valider (otpRequis = true).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuthResponse(
        String token,
        String username,
        String role,
        boolean otpRequis,
        String challengeId,
        String emailMasque
) {
    public static AuthResponse authentifie(String token, String username, String role) {
        return new AuthResponse(token, username, role, false, null, null);
    }

    public static AuthResponse defiOtp(String username, String challengeId, String emailMasque) {
        return new AuthResponse(null, username, null, true, challengeId, emailMasque);
    }
}
