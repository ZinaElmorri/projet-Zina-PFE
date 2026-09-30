package com.zina.sepa.domain.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Schéma XSD importé et versionné.
 */
@Entity
@Table(name = "xsd_schemas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class XsdSchema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private int version;

    /** Chemin de stockage du fichier XSD. */
    private String cheminFichier;

    /** Contenu brut du XSD. */
    @Column(columnDefinition = "TEXT")
    private String contenu;

    private String importePar;

    private LocalDateTime importeLe;

    @PrePersist
    void onCreate() {
        this.importeLe = LocalDateTime.now();
    }
}
