package com.zina.sepa.domain.entity;

import com.zina.sepa.domain.enums.Role;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateurs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String nomComplet;

    @Column(unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    /** Encodage de référence du visage (facultatif) pour l'auth biométrique. */
    @Column(columnDefinition = "TEXT")
    private String faceEncoding;

    @Column(nullable = false)
    private boolean actif = true;

    private LocalDateTime creeLe;

    private LocalDateTime derniereConnexion;

    @PrePersist
    void onCreate() {
        this.creeLe = LocalDateTime.now();
    }
}
