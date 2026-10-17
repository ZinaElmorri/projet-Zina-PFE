package com.zina.sepa.recommendation;

import com.zina.sepa.ai.AiAnomalyService;
import com.zina.sepa.ai.dto.AnalyseResult;
import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.recommendation.dto.AnomalieDto;
import com.zina.sepa.repository.TransactionRepository;
import com.zina.sepa.service.BusinessControlService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnomalieServiceTest {

    @Mock
    TransactionRepository transactionRepository;
    @Mock
    AiAnomalyService aiAnomalyService;
    @Mock
    RecommendationEngine recommendationEngine;
    @Mock
    AnomalieRepository anomalieRepository;

    // Règles métier réelles (IBAN/BIC) — pas de mock nécessaire.
    BusinessControlService businessControlService = new BusinessControlService();

    private AnomalieService service() {
        return new AnomalieService(transactionRepository, businessControlService,
                aiAnomalyService, recommendationEngine, anomalieRepository);
    }

    private Transaction transactionIbanInvalide() {
        Transaction t = new Transaction();
        t.setId(1L);
        t.setEmployeId("EMP1");
        t.setIban("FR0000");
        t.setMontant(new BigDecimal("2000"));
        return t;
    }

    @Test
    void ignorer_sans_commentaire_est_refuse() {
        Anomalie a = Anomalie.builder().id(9L).type(AnomalieType.MONTANT_SUSPECT).statut(AnomalieStatut.OUVERTE).build();
        when(anomalieRepository.findById(9L)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service().changerStatut(9L, "IGNOREE", "  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("commentaire");
    }

    @Test
    void marquer_corrigee_met_a_jour_le_statut() {
        Anomalie a = Anomalie.builder().id(9L).type(AnomalieType.IBAN_INVALIDE).statut(AnomalieStatut.OUVERTE).build();
        when(anomalieRepository.findById(9L)).thenReturn(Optional.of(a));
        when(anomalieRepository.save(any(Anomalie.class))).thenAnswer(i -> i.getArgument(0));

        AnomalieDto dto = service().changerStatut(9L, "CORRIGEE", "IBAN mis à jour");

        assertThat(dto.statut()).isEqualTo("CORRIGEE");
        assertThat(a.getStatut()).isEqualTo(AnomalieStatut.CORRIGEE);
        assertThat(a.getCorrigeeLe()).isNotNull();
    }

    @Test
    void detection_continue_meme_si_le_moteur_leve_une_exception() {
        when(transactionRepository.findAll()).thenReturn(List.of(transactionIbanInvalide()));
        lenient().when(aiAnomalyService.analyser(any())).thenReturn(new AnalyseResult(false, null, List.of()));
        when(anomalieRepository.findAll()).thenReturn(List.of());
        when(anomalieRepository.save(any(Anomalie.class))).thenAnswer(i -> i.getArgument(0));
        // Le moteur plante systématiquement.
        when(recommendationEngine.recommander(any())).thenThrow(new RuntimeException("boom"));

        // Ne doit pas propager d'exception et doit tout de même persister l'anomalie.
        service().detecter();

        verify(anomalieRepository, atLeastOnce()).save(any(Anomalie.class));
    }

    @Test
    void ia_indisponible_ne_cloture_pas_les_anomalies_ia_ouvertes() {
        Anomalie ia = Anomalie.builder().id(5L).signature("MONTANT_SUSPECT|7")
                .type(AnomalieType.MONTANT_SUSPECT).statut(AnomalieStatut.OUVERTE).build();
        Anomalie regle = Anomalie.builder().id(6L).signature("IBAN_INVALIDE|8")
                .type(AnomalieType.IBAN_INVALIDE).statut(AnomalieStatut.OUVERTE).build();
        when(transactionRepository.findAll()).thenReturn(List.of());
        when(aiAnomalyService.analyser(any())).thenReturn(new AnalyseResult(false, null, List.of()));
        when(anomalieRepository.findAll()).thenReturn(List.of(ia, regle));

        service().detecter();

        assertThat(ia.getStatut()).isEqualTo(AnomalieStatut.OUVERTE);
        assertThat(regle.getStatut()).isEqualTo(AnomalieStatut.CORRIGEE);
        assertThat(regle.getCorrigeePar()).isNull();
    }
}
