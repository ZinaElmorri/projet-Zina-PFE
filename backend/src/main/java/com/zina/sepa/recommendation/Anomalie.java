package com.zina.sepa.recommendation;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Anomalie détectée (règles métier RH, validation XSD ou IA), avec recommandation
 * de correction et suivi de statut.
 *
 * <p>Table portable PostgreSQL / Oracle (types VARCHAR, NUMERIC, TIMESTAMP).</p>
 */
@Entity
@Table(name = "anomalies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Anomalie {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Signature stable pour dédupliquer entre deux détections. */
    @Column(length = 200)
    private String signature;

    @Column(nullable = false, length = 40)
    private String type;

    @Column(length = 60)
    private String matricule;

    @Column(length = 40)
    private String iban;

    @Column(precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(length = 10)
    private String periode;

    /** Détail brut de l'anomalie (raisons IA, message XSD, etc.). */
    @Column(columnDefinition = "TEXT")
    private String details;

    private Long transactionId;

    // --- Colonnes de suivi (nullables) ---

    @Column(columnDefinition = "TEXT")
    private String recommandation;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private AnomalieStatut statut;

    @Column(columnDefinition = "TEXT")
    private String commentaireCorrection;

    @Column(length = 60)
    private String corrigeePar;

    private LocalDateTime corrigeeLe;

    private LocalDateTime creeLe;

    private LocalDateTime modifieLe;

    // --- Champs de contexte pour le moteur (non persistés) ---

    @Transient
    private String bic;
    @Transient
    private Integer nb;
    @Transient
    private String matricules;
    @Transient
    private String raisons;
    @Transient
    private String elementXsd;
    @Transient
    private String messageXsd;
    @Transient
    private BigDecimal net;
    @Transient
    private BigDecimal brut;

    @PrePersist
    void onCreate() {
        this.creeLe = LocalDateTime.now();
        this.modifieLe = this.creeLe;
        if (this.statut == null) {
            this.statut = AnomalieStatut.OUVERTE;
        }
    }

    @PreUpdate
    void onUpdate() {
        this.modifieLe = LocalDateTime.now();
    }
}
