package com.zina.sepa.recommendation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AnomalieRepository extends JpaRepository<Anomalie, Long> {
    List<Anomalie> findByStatut(AnomalieStatut statut);
    Optional<Anomalie> findBySignature(String signature);
    long countByStatut(AnomalieStatut statut);
}
