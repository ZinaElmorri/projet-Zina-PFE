package com.zina.sepa.dashboard;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.service.BusinessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KpiIaServiceTest {

    private static final String IBAN_VALIDE = "FR7630006000011234567890189";

    @Mock KpiIaRepository repository;

    private Transaction tx(String iban, String montant) {
        Transaction t = new Transaction();
        t.setEmployeId("E");
        t.setIban(iban);
        t.setMontant(new BigDecimal(montant));
        return t;
    }

    @Test
    void part_detectee_par_l_ia_seule_parmi_les_fiches_signalees() {
        Transaction horsRegles = tx(IBAN_VALIDE, "7500.00");                  // IA seule
        Transaction montantNul = tx(IBAN_VALIDE, "0");                        // vue aussi par les règles
        Transaction ibanInvalide = tx("FR7612345678901234567890123", "900"); // vue aussi par les règles
        Transaction doublon = tx(IBAN_VALIDE, "3100.00");                     // vue aussi par les règles (doublon)
        when(repository.suspectsIa(any(), any(), any(), any()))
                .thenReturn(List.of(horsRegles, montantNul, ibanInvalide, doublon));
        when(repository.countIdentiques(any(), eq(new BigDecimal("7500.00")), any(), any(), any(), any())).thenReturn(1L);
        when(repository.countIdentiques(any(), eq(new BigDecimal("3100.00")), any(), any(), any(), any())).thenReturn(2L);

        KpiIaService.KpiIa kpi = new KpiIaService(repository, new BusinessControlService()).kpi("2026-11", null);

        assertThat(kpi.periode()).isEqualTo("2026-11");
        assertThat(kpi.anomaliesIa()).isEqualTo(4);
        assertThat(kpi.anomaliesIaSeule()).isEqualTo(1);
        assertThat(kpi.partIaSeule()).isEqualByComparingTo("25.0");
    }

    @Test
    void sans_fiche_signalee_la_part_est_indisponible_et_non_zero() {
        when(repository.suspectsIa(any(), any(), any(), any())).thenReturn(List.of());

        KpiIaService.KpiIa kpi = new KpiIaService(repository, new BusinessControlService()).kpi("2026-11", " ");

        assertThat(kpi.anomaliesIa()).isZero();
        assertThat(kpi.partIaSeule()).isNull();
    }
}
