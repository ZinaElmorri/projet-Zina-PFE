package com.zina.sepa.dashboard;

import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.recommendation.Anomalie;
import com.zina.sepa.recommendation.AnomalieStatut;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Requêtes d'agrégation en lecture seule sur les anomalies (dashboard KPI).
 * Le filtre client passe par une sous-requête {@code exists} sur la transaction liée.
 */
public interface DashboardAnomalieRepository extends JpaRepository<Anomalie, Long> {

    // Fiches en erreur = fiches DISTINCTES (pas le nombre d'anomalies) parmi les fiches
    // APPROUVEE de la même période (même population que countFiches), ayant au moins une
    // anomalie bloquante non IGNOREE. Garantit taux = erreurs / fiches toujours <= 100 %.
    @Query("select count(distinct a.transactionId) from Anomalie a, Transaction t "
            + "where a.transactionId = t.id and t.statut = :statut "
            + "and t.creeLe >= :debut and t.creeLe < :fin "
            + "and a.type in :types and a.statut <> :ignoree "
            + "and (:clientId is null or t.clientId = :clientId)")
    long countErreursBloquantes(@Param("debut") LocalDateTime debut,
                                @Param("fin") LocalDateTime fin,
                                @Param("statut") TransactionStatus statut,
                                @Param("types") Collection<String> types,
                                @Param("ignoree") AnomalieStatut ignoree,
                                @Param("clientId") String clientId);

    @Query("select a.type, count(a) from Anomalie a "
            + "where a.periode = :periode "
            + "and (:clientId is null or exists (select 1 from Transaction t "
            + "     where t.id = a.transactionId and t.clientId = :clientId)) "
            + "group by a.type")
    List<Object[]> anomaliesParType(@Param("periode") String periode,
                                    @Param("clientId") String clientId);

    @Query("select a.statut, count(a) from Anomalie a "
            + "where a.periode = :periode "
            + "and (:clientId is null or exists (select 1 from Transaction t "
            + "     where t.id = a.transactionId and t.clientId = :clientId)) "
            + "group by a.statut")
    List<Object[]> anomaliesParStatut(@Param("periode") String periode,
                                      @Param("clientId") String clientId);

    @Query("select t.clientId, count(a) from Anomalie a, Transaction t "
            + "where t.id = a.transactionId and a.periode = :periode and a.statut <> :ignoree "
            + "and t.clientId is not null and (:clientId is null or t.clientId = :clientId) "
            + "group by t.clientId order by count(a) desc")
    List<Object[]> topClients(@Param("periode") String periode,
                              @Param("ignoree") AnomalieStatut ignoree,
                              @Param("clientId") String clientId,
                              Pageable pageable);

    // Variante mensuelle de countErreursBloquantes, pour l'évolution 12 mois (même règle).
    @Query("select year(t.creeLe), month(t.creeLe), count(distinct a.transactionId) from Anomalie a, Transaction t "
            + "where a.transactionId = t.id and t.statut = :statut "
            + "and t.creeLe >= :debut and t.creeLe < :fin "
            + "and a.type in :types and a.statut <> :ignoree "
            + "and (:clientId is null or t.clientId = :clientId) "
            + "group by year(t.creeLe), month(t.creeLe)")
    List<Object[]> erreursParMois(@Param("debut") LocalDateTime debut,
                                  @Param("fin") LocalDateTime fin,
                                  @Param("statut") TransactionStatus statut,
                                  @Param("types") Collection<String> types,
                                  @Param("ignoree") AnomalieStatut ignoree,
                                  @Param("clientId") String clientId);
}
