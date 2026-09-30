package com.zina.sepa.domain.entity;

import com.zina.sepa.domain.enums.RiskLevel;
import com.zina.sepa.domain.enums.TransactionStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Transaction de virement SEPA (une ligne de paiement).
 */
@Entity
@Table(name = "transactions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifiant de l'employé bénéficiaire (côté SIRH). */
    @Column(nullable = false)
    private String employeId;

    /** Identifiant du client/entreprise (nullable, pour les KPI par client). */
    @Column(length = 60)
    private String clientId;

    @Column(nullable = false)
    private String nomBeneficiaire;

    @Column(nullable = false)
    private String iban;

    private String bic;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal montant;

    @Column(nullable = false)
    private String devise = "EUR";

    private String libelle;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus statut = TransactionStatus.BROUILLON;

    /** Score de risque [0,1] attribué par le module IA. */
    private Double scoreRisque;

    @Enumerated(EnumType.STRING)
    private RiskLevel niveauRisque;

    // --- Champs additifs (module IA « montants suspects »), tous nullables ---

    /** Type d'anomalie (ex. MONTANT_SUSPECT). */
    @Column(length = 40)
    private String typeAnomalie;

    /** Origine de l'anomalie : 'REGLE' (contrôles métier) ou 'IA'. */
    @Column(length = 10)
    private String sourceAnomalie;

    /** Version du modèle IA ayant produit le score. */
    @Column(length = 40)
    private String modelVersion;

    /** Explications lisibles fournies par le modèle. */
    @Column(columnDefinition = "TEXT")
    private String raisonsAnomalie;

    /** Recommandation associée à l'anomalie. */
    @Column(columnDefinition = "TEXT")
    private String recommandation;

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
