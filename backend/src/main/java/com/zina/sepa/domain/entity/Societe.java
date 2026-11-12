package com.zina.sepa.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Société émettrice (débiteur) des virements de masse. Le code correspond au clientId des transactions.
 */
@Entity
@Table(name = "societes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Societe {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String iban;

    @Column(nullable = false)
    private String bic;

    @Builder.Default
    @Column(nullable = false)
    private boolean actif = true;

    private LocalDateTime creeLe;

    private LocalDateTime modifieLe;

    @PrePersist
    void onCreate() {
        this.creeLe = LocalDateTime.now();
        this.modifieLe = this.creeLe;
    }

    @PreUpdate
    void onUpdate() {
        this.modifieLe = LocalDateTime.now();
    }
}
