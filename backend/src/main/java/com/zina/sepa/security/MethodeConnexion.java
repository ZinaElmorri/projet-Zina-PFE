package com.zina.sepa.security;

/**
 * Méthode utilisée pour prouver l'identité lors de la connexion. Point unique de la règle
 * « second facteur » : c'est le backend, jamais le front, qui décide si un code OTP est exigé.
 */
public enum MethodeConnexion {

    /** Identifiant + mot de passe : un code OTP est toujours exigé avant d'ouvrir la session. */
    MOT_DE_PASSE(true),

    /** Reconnaissance faciale réussie : le visage est le facteur d'authentification, pas d'OTP. */
    FACIALE(false);

    private final boolean exigeOtp;

    MethodeConnexion(boolean exigeOtp) {
        this.exigeOtp = exigeOtp;
    }

    public boolean exigeOtp() {
        return exigeOtp;
    }
}
