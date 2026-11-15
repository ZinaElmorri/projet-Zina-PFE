package com.zina.sepa.dashboard;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.enums.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Requêtes en lecture seule des indicateurs « IA » du tableau de bord KPI. */
public interface KpiIaRepository extends JpaRepository<Transaction, Long> {

    /** Fiches signalées par la dernière analyse IA (source « IA ») sur la période, hors supprimées. */
    @Query("select t from Transaction t "
            + "where t.sourceAnomalie = 'IA' and t.statut <> :exclu and t.creeLe >= :debut and t.creeLe < :fin "
            + "and (:clientId is null or t.clientId = :clientId)")
    List<Transaction> suspectsIa(@Param("exclu") TransactionStatus exclu,
                                 @Param("debut") LocalDateTime debut,
                                 @Param("fin") LocalDateTime fin,
                                 @Param("clientId") String clientId);

    /** Fiches de la période portant le même IBAN et le même montant (règle de doublon des contrôles métier). */
    @Query("select count(t) from Transaction t "
            + "where t.iban = :iban and t.montant = :montant and t.statut <> :exclu "
            + "and t.creeLe >= :debut and t.creeLe < :fin and (:clientId is null or t.clientId = :clientId)")
    long countIdentiques(@Param("iban") String iban,
                         @Param("montant") java.math.BigDecimal montant,
                         @Param("exclu") TransactionStatus exclu,
                         @Param("debut") LocalDateTime debut,
                         @Param("fin") LocalDateTime fin,
                         @Param("clientId") String clientId);
}
