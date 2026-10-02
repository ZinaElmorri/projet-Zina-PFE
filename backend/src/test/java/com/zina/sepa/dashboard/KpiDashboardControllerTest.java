package com.zina.sepa.dashboard;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class KpiDashboardControllerTest {

    @Mock
    DashboardTransactionRepository transactionRepository;
    @Mock
    DashboardAnomalieRepository anomalieRepository;

    @Test
    void periode_par_defaut_est_le_mois_dernier() throws Exception {
        when(transactionRepository.countFiches(any(), any(), any(), any())).thenReturn(0L);
        when(transactionRepository.countClients(any(), any(), any(), any())).thenReturn(0L);
        when(transactionRepository.sumMontant(any(), any(), any(), any())).thenReturn(BigDecimal.ZERO);
        when(transactionRepository.countFichesParMois(any(), any(), any(), any())).thenReturn(List.of());
        when(anomalieRepository.countErreursBloquantes(any(), any(), any(), any(), any(), any())).thenReturn(0L);
        when(anomalieRepository.anomaliesParType(any(), any())).thenReturn(List.of());
        when(anomalieRepository.anomaliesParStatut(any(), any())).thenReturn(List.of());
        when(anomalieRepository.topClients(any(), any(), any(), any())).thenReturn(List.of());
        when(anomalieRepository.erreursParMois(any(), any(), any(), any(), any(), any())).thenReturn(List.of());

        KpiDashboardService service = new KpiDashboardService(transactionRepository, anomalieRepository);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new KpiDashboardController(service)).build();

        String moisDernier = YearMonth.now().minusMonths(1).format(DateTimeFormatter.ofPattern("yyyy-MM"));

        mockMvc.perform(get("/api/dashboard/kpi"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periode").value(moisDernier));
    }
}
