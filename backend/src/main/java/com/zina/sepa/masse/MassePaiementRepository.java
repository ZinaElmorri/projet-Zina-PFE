package com.zina.sepa.masse;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Requêtes de filtrage des paiements pour la génération de masse (nouveau code, package masse).
 * N'ajoute rien aux repositories existants.
 */
public interface MassePaiementRepository extends JpaRepository<Transaction, Long> {

    @Query("select t from Transaction t "
            + "where t.statut in :statuts and t.creeLe >= :debut and t.creeLe < :fin "
            + "and (:clientId is null or t.clientId = :clientId) "
            + "and (:sansMatricule = true or t.employeId in :matricules) "
            + "order by t.employeId asc")
    List<Transaction> filtrer(@Param("statuts") Collection<TransactionStatus> statuts,
                              @Param("debut") LocalDateTime debut,
                              @Param("fin") LocalDateTime fin,
                              @Param("clientId") String clientId,
                              @Param("sansMatricule") boolean sansMatricule,
                              @Param("matricules") Collection<String> matricules);

    @Query("select distinct t.clientId from Transaction t where t.clientId is not null order by t.clientId")
    List<String> societes();

    @Query("select distinct year(t.creeLe), month(t.creeLe) from Transaction t "
            + "where (:clientId is null or t.clientId = :clientId)")
    List<Object[]> periodes(@Param("clientId") String clientId);

    @Query("select distinct t.employeId, t.nomBeneficiaire from Transaction t "
            + "where t.creeLe >= :debut and t.creeLe < :fin "
            + "and (:clientId is null or t.clientId = :clientId) order by t.employeId")
    List<Object[]> matricules(@Param("clientId") String clientId,
                              @Param("debut") LocalDateTime debut,
                              @Param("fin") LocalDateTime fin);
}
