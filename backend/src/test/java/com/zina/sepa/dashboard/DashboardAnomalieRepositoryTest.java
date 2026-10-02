package com.zina.sepa.dashboard;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.recommendation.Anomalie;
import com.zina.sepa.recommendation.AnomalieStatut;
import com.zina.sepa.recommendation.AnomalieType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Vérifie, au niveau SQL, les règles du taux d'erreur qu'un test par mock ne peut pas couvrir :
 * comptage de fiches DISTINCTES (pas d'anomalies) et périmètre strictement limité à la période
 * et aux fiches APPROUVEE (même population que le dénominateur countFiches).
 */
@DataJpaTest
class DashboardAnomalieRepositoryTest {

    @Autowired
    DashboardTransactionRepository transactionRepository;
    @Autowired
    DashboardAnomalieRepository anomalieRepository;

    private Transaction fiche(TransactionStatus statut) {
        return transactionRepository.save(Transaction.builder()
                .employeId("EMP1")
                .nomBeneficiaire("Jean Dupont")
                .iban("FR7630006000011234567890189")
                .montant(BigDecimal.valueOf(1000))
                .devise("EUR")
                .statut(statut)
                .build());
    }

    /** Force creeLe après l'insertion : le @PrePersist de Transaction l'écrase sinon à now(). */
    private void forcerCreeLe(Transaction t, LocalDateTime creeLe) {
        t.setCreeLe(creeLe);
        transactionRepository.save(t);
    }

    private void anomalie(Transaction t, String type, AnomalieStatut statut) {
        anomalieRepository.save(Anomalie.builder()
                .type(type)
                .transactionId(t.getId())
                .statut(statut)
                .build());
    }

    @Test
    void plusieurs_anomalies_sur_la_meme_fiche_comptent_pour_une_seule() {
        YearMonth mois = YearMonth.now();
        LocalDateTime debut = mois.atDay(1).atStartOfDay();
        LocalDateTime fin = mois.plusMonths(1).atDay(1).atStartOfDay();

        Transaction t = fiche(TransactionStatus.APPROUVEE);
        forcerCreeLe(t, debut.plusDays(1));
        anomalie(t, AnomalieType.IBAN_INVALIDE, AnomalieStatut.OUVERTE);
        anomalie(t, AnomalieType.MONTANT_INVALIDE, AnomalieStatut.OUVERTE);

        long erreurs = anomalieRepository.countErreursBloquantes(
                debut, fin, TransactionStatus.APPROUVEE,
                List.of(AnomalieType.IBAN_INVALIDE, AnomalieType.MONTANT_INVALIDE),
                AnomalieStatut.IGNOREE, null);

        assertThat(erreurs).isEqualTo(1L);
    }

    @Test
    void anomalies_hors_periode_sont_exclues() {
        YearMonth moisCourant = YearMonth.now();
        YearMonth moisPrecedent = moisCourant.minusMonths(1);
        LocalDateTime debut = moisCourant.atDay(1).atStartOfDay();
        LocalDateTime fin = moisCourant.plusMonths(1).atDay(1).atStartOfDay();

        Transaction t = fiche(TransactionStatus.APPROUVEE);
        forcerCreeLe(t, moisPrecedent.atDay(10).atStartOfDay());
        anomalie(t, AnomalieType.IBAN_INVALIDE, AnomalieStatut.OUVERTE);

        long erreurs = anomalieRepository.countErreursBloquantes(
                debut, fin, TransactionStatus.APPROUVEE,
                List.of(AnomalieType.IBAN_INVALIDE), AnomalieStatut.IGNOREE, null);

        assertThat(erreurs).isZero();
    }

    @Test
    void anomalies_ignorees_sont_exclues() {
        YearMonth mois = YearMonth.now();
        LocalDateTime debut = mois.atDay(1).atStartOfDay();
        LocalDateTime fin = mois.plusMonths(1).atDay(1).atStartOfDay();

        Transaction t = fiche(TransactionStatus.APPROUVEE);
        forcerCreeLe(t, debut.plusDays(1));
        anomalie(t, AnomalieType.IBAN_INVALIDE, AnomalieStatut.IGNOREE);

        long erreurs = anomalieRepository.countErreursBloquantes(
                debut, fin, TransactionStatus.APPROUVEE,
                List.of(AnomalieType.IBAN_INVALIDE), AnomalieStatut.IGNOREE, null);

        assertThat(erreurs).isZero();
    }

    @Test
    void seules_les_erreurs_des_fiches_approuvees_comptent_taux_jamais_superieur_a_100() {
        YearMonth mois = YearMonth.now();
        LocalDateTime debut = mois.atDay(1).atStartOfDay();
        LocalDateTime fin = mois.plusMonths(1).atDay(1).atStartOfDay();

        Transaction approuvee = fiche(TransactionStatus.APPROUVEE);
        forcerCreeLe(approuvee, debut.plusDays(1));
        anomalie(approuvee, AnomalieType.IBAN_INVALIDE, AnomalieStatut.OUVERTE);

        // Fiche rejetée du même mois, avec plusieurs erreurs : ne doit pas être comptée,
        // sinon le numérateur dépasserait le dénominateur (fiches APPROUVEE = 1).
        Transaction rejetee = fiche(TransactionStatus.REJETEE);
        forcerCreeLe(rejetee, debut.plusDays(2));
        anomalie(rejetee, AnomalieType.IBAN_INVALIDE, AnomalieStatut.OUVERTE);
        anomalie(rejetee, AnomalieType.MONTANT_INVALIDE, AnomalieStatut.OUVERTE);

        long fiches = transactionRepository.countFiches(TransactionStatus.APPROUVEE, debut, fin, null);
        long erreurs = anomalieRepository.countErreursBloquantes(
                debut, fin, TransactionStatus.APPROUVEE,
                List.of(AnomalieType.IBAN_INVALIDE, AnomalieType.MONTANT_INVALIDE),
                AnomalieStatut.IGNOREE, null);

        assertThat(fiches).isEqualTo(1L);
        assertThat(erreurs).isEqualTo(1L);
    }
}
