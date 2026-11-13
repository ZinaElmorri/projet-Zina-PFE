package com.zina.sepa.dashboard;

import com.zina.sepa.dashboard.dto.KpiResponse;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.recommendation.AnomalieStatut;
import com.zina.sepa.recommendation.AnomalieType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiDashboardServiceTest {

    @Mock
    DashboardTransactionRepository transactionRepository;
    @Mock
    DashboardAnomalieRepository anomalieRepository;
    @Captor
    ArgumentCaptor<Collection<String>> typesCaptor;

    private KpiDashboardService service() {
        return new KpiDashboardService(transactionRepository, anomalieRepository);
    }

    private void mocksVides() {
        lenient().when(transactionRepository.sumMontant(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        lenient().when(transactionRepository.countClients(any(), any(), any(), any())).thenReturn(0L);
        lenient().when(transactionRepository.countFichesParMois(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(anomalieRepository.anomaliesParType(any(), any())).thenReturn(List.of());
        lenient().when(anomalieRepository.anomaliesParStatut(any(), any())).thenReturn(List.of());
        lenient().when(anomalieRepository.topClients(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(anomalieRepository.erreursParMois(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
    }

    @Test
    void taux_erreur_2_sur_1100_vaut_0_18() {
        mocksVides();
        when(transactionRepository.countFiches(any(), any(), any(), any())).thenReturn(1100L);
        when(anomalieRepository.countErreursBloquantes(any(), any(), any(), any(), any(), any())).thenReturn(2L);

        KpiResponse kpi = service().kpi(new DashboardFilter("2026-08", null, null, null));

        assertThat(kpi.fichesGenerees()).isEqualTo(1100L);
        assertThat(kpi.nbErreurs()).isEqualTo(2L);
        assertThat(kpi.tauxErreur()).isEqualByComparingTo("0.18");
    }

    @Test
    void aucune_fiche_pas_de_division_par_zero() {
        mocksVides();
        when(transactionRepository.countFiches(any(), any(), any(), any())).thenReturn(0L);
        when(anomalieRepository.countErreursBloquantes(any(), any(), any(), any(), any(), any())).thenReturn(0L);

        KpiResponse kpi = service().kpi(new DashboardFilter("2026-08", null, null, null));

        assertThat(kpi.tauxErreur()).isEqualByComparingTo("0.00");
    }

    @Test
    void taux_jamais_superieur_a_100_meme_si_le_numerateur_brut_depasse_le_denominateur() {
        mocksVides();
        when(transactionRepository.countFiches(any(), any(), any(), any())).thenReturn(2L);
        // Valeur de repli défensive : même si la requête SQL (qui garantit normalement
        // nbErreurs <= fiches) renvoyait une valeur aberrante, le taux reste borné.
        when(anomalieRepository.countErreursBloquantes(any(), any(), any(), any(), any(), any())).thenReturn(45L);

        KpiResponse kpi = service().kpi(new DashboardFilter("2026-08", null, null, null));

        assertThat(kpi.tauxErreur()).isEqualByComparingTo("100.00");
    }

    @Test
    void erreurs_bloquantes_excluent_ignorees_et_avertissements() {
        mocksVides();
        when(transactionRepository.countFiches(any(), any(), any(), any())).thenReturn(10L);
        when(anomalieRepository.countErreursBloquantes(
                any(), any(), any(), typesCaptor.capture(), eq(AnomalieStatut.IGNOREE), any()))
                .thenReturn(1L);

        service().kpi(new DashboardFilter("2026-08", null, null, null));

        // Le statut IGNOREE est bien transmis pour être exclu, et les avertissements ne comptent pas.
        assertThat(typesCaptor.getValue())
                .doesNotContain(AnomalieType.MONTANT_SUSPECT, AnomalieType.IBAN_PARTAGE)
                .contains(AnomalieType.IBAN_INVALIDE, AnomalieType.DOUBLON_PAIEMENT);
    }

    @Test
    void numerateur_et_denominateur_portent_sur_les_memes_fiches_approuvees() {
        mocksVides();
        ArgumentCaptor<TransactionStatus> statutFichesCaptor = ArgumentCaptor.forClass(TransactionStatus.class);
        ArgumentCaptor<TransactionStatus> statutErreursCaptor = ArgumentCaptor.forClass(TransactionStatus.class);
        when(transactionRepository.countFiches(statutFichesCaptor.capture(), any(), any(), any())).thenReturn(1100L);
        when(anomalieRepository.countErreursBloquantes(
                any(), any(), statutErreursCaptor.capture(), any(), any(), any())).thenReturn(2L);

        service().kpi(new DashboardFilter("2026-08", null, null, null));

        assertThat(statutFichesCaptor.getValue()).isEqualTo(TransactionStatus.APPROUVEE);
        assertThat(statutErreursCaptor.getValue()).isEqualTo(TransactionStatus.APPROUVEE);
    }

    @Test
    void lots_xsd_et_total_transactions_de_la_periode_sont_renseignes() {
        mocksVides();
        com.zina.sepa.masse.FichierSepaGenereRepository fichiers = org.mockito.Mockito.mock(com.zina.sepa.masse.FichierSepaGenereRepository.class);
        when(fichiers.countLots(any(), any(), any())).thenReturn(4L);
        when(fichiers.countLotsValides(any(), any(), any())).thenReturn(3L);
        when(transactionRepository.countTotal(eq(TransactionStatus.SUPPRIMEE), any(), any(), any())).thenReturn(9L);
        when(transactionRepository.countFiches(any(), any(), any(), any())).thenReturn(5L);
        when(anomalieRepository.countErreursBloquantes(any(), any(), any(), any(), any(), any())).thenReturn(0L);

        KpiResponse kpi = new KpiDashboardService(transactionRepository, anomalieRepository, fichiers)
                .kpi(new DashboardFilter("2026-11", null, null, null));

        assertThat(kpi.lotsGeneres()).isEqualTo(4L);
        assertThat(kpi.lotsValidesXsd()).isEqualTo(3L);
        assertThat(kpi.transactionsTotal()).isEqualTo(9L);
    }

    @Test
    void sans_historique_de_fichiers_les_lots_restent_indisponibles() {
        mocksVides();
        when(transactionRepository.countFiches(any(), any(), any(), any())).thenReturn(0L);
        when(anomalieRepository.countErreursBloquantes(any(), any(), any(), any(), any(), any())).thenReturn(0L);

        KpiResponse kpi = service().kpi(new DashboardFilter("2026-11", null, null, null));

        assertThat(kpi.lotsGeneres()).isNull();
        assertThat(kpi.lotsValidesXsd()).isNull();
    }

    @Test
    void filtre_par_client_est_transmis_aux_requetes() {
        mocksVides();
        ArgumentCaptor<String> clientCaptor = ArgumentCaptor.forClass(String.class);
        when(transactionRepository.countFiches(eq(TransactionStatus.APPROUVEE),
                any(LocalDateTime.class), any(LocalDateTime.class), clientCaptor.capture())).thenReturn(5L);
        when(anomalieRepository.countErreursBloquantes(any(), any(), any(), any(), any(), any())).thenReturn(0L);

        service().kpi(new DashboardFilter("2026-08", "CLIENT_42", null, null));

        assertThat(clientCaptor.getValue()).isEqualTo("CLIENT_42");
    }
}

