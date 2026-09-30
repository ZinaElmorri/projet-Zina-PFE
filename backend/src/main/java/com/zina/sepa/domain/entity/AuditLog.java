package com.zina.sepa.domain.entity;

import com.zina.sepa.domain.enums.AuditAction;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Enregistrement du journal d'audit (audit trail).
 */
@Entity
@Table(name = "audit_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Column(columnDefinition = "TEXT")
    private String details;

    private String adresseIp;

    /** Résultat de l'action : SUCCES / ECHEC. */
    private String resultat;

    @Column(nullable = false)
    private LocalDateTime horodatage;

    @PrePersist
    void onCreate() {
        if (this.horodatage == null) {
            this.horodatage = LocalDateTime.now();
        }
    }
}
