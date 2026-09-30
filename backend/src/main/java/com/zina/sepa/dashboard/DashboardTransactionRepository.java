package com.zina.sepa.dashboard;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Requêtes d'agrégation en lecture seule sur les transactions (dashboard KPI).
 * JPQL portable (COUNT/SUM/GROUP BY, year()/month()) — aucune fonction propre à PostgreSQL.
 */
public interface DashboardTransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("select count(t) from Transaction t "
            + "where t.statut = :statut and t.creeLe >= :debut and t.creeLe < :fin "
            + "and (:clientId is null or t.clientId = :clientId)")
    long countFiches(@Param("statut") TransactionStatus statut,
                     @Param("debut") LocalDateTime debut,
                     @Param("fin") LocalDateTime fin,
                     @Param("clientId") String clientId);

    @Query("select count(distinct t.clientId) from Transaction t "
            + "where t.statut = :statut and t.creeLe >= :debut and t.creeLe < :fin "
            + "and t.clientId is not null and (:clientId is null or t.clientId = :clientId)")
    long countClients(@Param("statut") TransactionStatus statut,
                      @Param("debut") LocalDateTime debut,
                      @Param("fin") LocalDateTime fin,
                      @Param("clientId") String clientId);

    @Query("select coalesce(sum(t.montant), 0) from Transaction t "
            + "where t.statut = :statut and t.creeLe >= :debut and t.creeLe < :fin "
            + "and (:clientId is null or t.clientId = :clientId)")
    BigDecimal sumMontant(@Param("statut") TransactionStatus statut,
                          @Param("debut") LocalDateTime debut,
                          @Param("fin") LocalDateTime fin,
                          @Param("clientId") String clientId);

    @Query("select year(t.creeLe), month(t.creeLe), count(t) from Transaction t "
            + "where t.statut = :statut and t.creeLe >= :debut and t.creeLe < :fin "
            + "and (:clientId is null or t.clientId = :clientId) "
            + "group by year(t.creeLe), month(t.creeLe)")
    List<Object[]> countFichesParMois(@Param("statut") TransactionStatus statut,
                                      @Param("debut") LocalDateTime debut,
                                      @Param("fin") LocalDateTime fin,
                                      @Param("clientId") String clientId);
}
