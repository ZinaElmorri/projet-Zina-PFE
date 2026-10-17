package com.zina.sepa.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnomalieRepository extends JpaRepository<Anomalie, Long> {
    List<Anomalie> findByStatut(AnomalieStatut statut);
    Optional<Anomalie> findBySignature(String signature);
    long countByStatut(AnomalieStatut statut);
    long countByTypeAndStatut(String type, AnomalieStatut statut);
    /** Corrections faites par un utilisateur (les corrections automatiques n'ont pas d'auteur). */
    long countByTypeAndStatutAndCorrigeeParIsNotNull(String type, AnomalieStatut statut);
    List<Anomalie> findByTypeAndStatutAndCorrigeeParIsNotNull(String type, AnomalieStatut statut);
}
