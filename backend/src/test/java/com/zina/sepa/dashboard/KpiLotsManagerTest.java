package com.zina.sepa.dashboard;

import com.zina.sepa.dashboard.dto.KpiResponse;
import com.zina.sepa.masse.FichierSepaGenereRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Lots validés par le Manager : distincts des lots seulement conformes au XSD. */
@ExtendWith(MockitoExtension.class)
class KpiLotsManagerTest {

    @Mock DashboardTransactionRepository transactionRepository;
    @Mock DashboardAnomalieRepository anomalieRepository;
    @Mock FichierSepaGenereRepository fichiers;

    @Test
    void les_lots_valides_par_le_manager_sont_comptes_a_part_des_lots_conformes_au_xsd() {
        when(fichiers.countLots(any(), any(), any())).thenReturn(6L);
        when(fichiers.countLotsValides(any(), any(), any())).thenReturn(6L);
        when(fichiers.countLotsValidesManager(any(), any(), any())).thenReturn(1L);

        KpiResponse kpi = new KpiDashboardService(transactionRepository, anomalieRepository, fichiers)
                .kpi(new DashboardFilter("2026-11", null, null, null));

        assertThat(kpi.lotsGeneres()).isEqualTo(6L);
        assertThat(kpi.lotsValidesXsd()).isEqualTo(6L);
        assertThat(kpi.lotsValidesManager()).isEqualTo(1L);
    }

    @Test
    void sans_historique_de_fichiers_les_lots_valides_par_le_manager_sont_indisponibles() {
        KpiResponse kpi = new KpiDashboardService(transactionRepository, anomalieRepository)
                .kpi(new DashboardFilter("2026-11", null, null, null));

        assertThat(kpi.lotsValidesManager()).isNull();
    }
}
