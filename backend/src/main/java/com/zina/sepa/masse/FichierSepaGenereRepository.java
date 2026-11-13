package com.zina.sepa.masse;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface FichierSepaGenereRepository extends JpaRepository<FichierSepaGenere, Long> {
    List<FichierSepaGenere> findAllByOrderByGenereLeDesc();

    long countByValideXsd(boolean valideXsd);

    // Lots (fichiers de l'historique SEPA) générés sur une période.
    @Query("select count(f) from FichierSepaGenere f "
            + "where f.genereLe >= :debut and f.genereLe < :fin "
            + "and (:clientId is null or f.clientId = :clientId)")
    long countLots(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin,
                   @Param("clientId") String clientId);

    @Query("select count(f) from FichierSepaGenere f "
            + "where f.valideXsd = true and f.genereLe >= :debut and f.genereLe < :fin "
            + "and (:clientId is null or f.clientId = :clientId)")
    long countLotsValides(@Param("debut") LocalDateTime debut, @Param("fin") LocalDateTime fin,
                          @Param("clientId") String clientId);
}
