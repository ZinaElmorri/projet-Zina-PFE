package com.zina.sepa.masse;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Historique d'un fichier SEPA de masse généré. Le contenu enregistré n'est jamais
 * modifié : un re-téléchargement renvoie exactement le même XML.
 *
 * <p>Nouvelle table {@code sepa_fichiers_generes} (types portables).</p>
 */
@Entity
@Table(name = "sepa_fichiers_generes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FichierSepaGenere {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 35)
    private String msgId;

    @Column(length = 200)
    private String nomFichier;

    @Column(columnDefinition = "TEXT")
    private String contenuXml;

    private Long xsdSchemaId;

    @Column(length = 120)
    private String xsdNom;

    @Column(length = 20)
    private String versionMessage;

    @Column(length = 60)
    private String clientId;

    @Column(length = 10)
    private String periode;

    /** Liste des matricules inclus (séparés par des virgules). */
    @Column(columnDefinition = "TEXT")
    private String matricules;

    /** Liste des identifiants de transactions inclus (séparés par des virgules). */
    @Column(columnDefinition = "TEXT")
    private String transactionIds;

    private int nbTransactions;

    @Column(precision = 18, scale = 2)
    private BigDecimal ctrlSum;

    @Column(length = 140)
    private String nomDebiteur;

    @Column(length = 40)
    private String ibanDebiteur;

    @Column(length = 12)
    private String dateExecution;

    private boolean valideXsd;

    /** Erreurs de validation XSD sérialisées en JSON. */
    @Column(columnDefinition = "TEXT")
    private String erreursXsd;

    @Column(length = 60)
    private String generePar;

    private LocalDateTime genereLe;

    @PrePersist
    void onCreate() {
        if (this.genereLe == null) {
            this.genereLe = LocalDateTime.now();
        }
    }
}
