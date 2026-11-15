package com.zina.sepa.dashboard;

import com.zina.sepa.dashboard.dto.KpiResponse;
import com.zina.sepa.masse.AccesSocietes;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Périmètre de l'Expert RH : les KPI ne portent que sur les sociétés qui lui sont affectées. */
@ExtendWith(MockitoExtension.class)
class KpiPerimetreExpertTest {

    @Mock DashboardTransactionRepository transactionRepository;
    @Mock DashboardAnomalieRepository anomalieRepository;
    @Mock AccesSocietes acces;

    @Test
    void les_kpi_d_un_expert_sont_la_somme_de_ses_societes_et_le_taux_est_recalcule() {
        when(transactionRepository.countFiches(any(), any(), any(), eq("SOC-001"))).thenReturn(2L);
        when(transactionRepository.countFiches(any(), any(), any(), eq("SOC-002"))).thenReturn(8L);
        when(anomalieRepository.countErreursBloquantes(any(), any(), any(), any(), any(), eq("SOC-001"))).thenReturn(1L);
        when(anomalieRepository.countErreursBloquantes(any(), any(), any(), any(), any(), eq("SOC-002"))).thenReturn(1L);
        when(transactionRepository.sumMontant(any(), any(), any(), eq("SOC-001"))).thenReturn(new BigDecimal("100.50"));
        when(transactionRepository.sumMontant(any(), any(), any(), eq("SOC-002"))).thenReturn(new BigDecimal("200.00"));
        when(transactionRepository.countClients(any(), any(), any(), any())).thenReturn(1L);
        when(transactionRepository.countTotal(any(), any(), any(), any())).thenReturn(5L);
        when(anomalieRepository.anomaliesParType(any(), any())).thenReturn(List.<Object[]>of(new Object[]{"IBAN_INVALIDE", 1L}));

        KpiResponse kpi = new KpiDashboardService(transactionRepository, anomalieRepository)
                .kpiPourSocietes(new DashboardFilter("2026-11", null, null, null), Set.of("SOC-001", "SOC-002"));

        assertThat(kpi.fichesGenerees()).isEqualTo(10L);
        assertThat(kpi.nbErreurs()).isEqualTo(2L);
        assertThat(kpi.tauxErreur()).isEqualByComparingTo("20.00");
        assertThat(kpi.montantTotalVire()).isEqualByComparingTo("300.50");
        assertThat(kpi.clientsConcernes()).isEqualTo(2L);
        assertThat(kpi.transactionsTotal()).isEqualTo(10L);
        assertThat(kpi.anomaliesParType()).singleElement().satisfies(v -> assertThat(v.value()).isEqualTo(2L));
        assertThat(kpi.evolutionMensuelle()).hasSize(12);
    }

    @Test
    void un_expert_sans_societe_affectee_ne_voit_aucune_donnee() {
        KpiResponse kpi = new KpiDashboardService(transactionRepository, anomalieRepository)
                .kpiPourSocietes(new DashboardFilter("2026-11", null, null, null), Set.of());

        assertThat(kpi.fichesGenerees()).isZero();
        assertThat(kpi.tauxErreur()).isEqualByComparingTo("0");
        assertThat(kpi.evolutionMensuelle()).hasSize(12);
        verify(transactionRepository, never()).countFiches(any(), any(), any(), any());
    }

    @Test
    void un_expert_ne_peut_pas_filtrer_sur_une_societe_qui_ne_lui_est_pas_affectee() {
        when(acces.perimetre()).thenReturn(Optional.of(Set.of("SOC-001")));
        doThrow(new AccessDeniedException("non affectée")).when(acces).verifier("SOC-002");

        KpiDashboardController controller = new KpiDashboardController(
                new KpiDashboardService(transactionRepository, anomalieRepository), acces);

        assertThatThrownBy(() -> controller.kpi("2026-11", "SOC-002", null, null))
                .isInstanceOf(AccessDeniedException.class);
    }
}
