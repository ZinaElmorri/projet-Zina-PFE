package com.zina.sepa.otp;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Défi OTP en attente : créé après validation du mot de passe, consommé à la vérification du code.
 */
@Entity
@Table(name = "otp_challenges")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OtpChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String challengeId;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private String email;

    /** Hash BCrypt du code (le code en clair n'est jamais stocké). */
    @Column(nullable = false)
    private String codeHash;

    @Column(nullable = false)
    private LocalDateTime expireLe;

    @Column(nullable = false)
    private LocalDateTime dernierEnvoi;

    private int tentatives;

    private int renvois;
}
