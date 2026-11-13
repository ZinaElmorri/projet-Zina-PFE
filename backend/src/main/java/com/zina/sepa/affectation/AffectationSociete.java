package com.zina.sepa.affectation;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Affectation d'une société à un Expert RH. Une société n'a qu'une affectation active (finLe nul) ;
 * les affectations terminées constituent l'historique.
 */
@Entity
@Table(name = "affectations_societe")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AffectationSociete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 60)
    private String societeCode;

    private Long expertId;

    @Column(length = 60)
    private String expertUsername;

    private LocalDateTime debutLe;

    /** Nul tant que l'affectation est active. */
    private LocalDateTime finLe;

    @Column(length = 60)
    private String affectePar;
}
