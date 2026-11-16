package com.zina.sepa.recommendation;

import com.zina.sepa.ai.AiAnomalyService;
import com.zina.sepa.ai.dto.AnalyseResult;
import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.masse.AccesSocietes;
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
import java.util.Set;

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
    @Mock
    AccesSocietes acces;

    // Règles métier réelles (IBAN/BIC) — pas de mock nécessaire.
    BusinessControlService businessControlService = new BusinessControlService();

    private AnomalieService service() {
        return new AnomalieService(transactionRepository, businessControlService,
                aiAnomalyService, recommendationEngine, anomalieRepository, acces);
    }

    private void expertDeSoc001() {
        when(acces.perimetre()).thenReturn(Optional.of(Set.of("SOC-001")));
        when(acces.filtrerTransactions(any())).thenAnswer(i -> ((List<Transaction>) i.getArgument(0)).stream()
                .filter(t -> "SOC-001".equals(t.getClientId())).toList());
    }

    @Test
    void un_expert_ne_voit_que_les_anomalies_de_ses_societes() {
        Transaction mienne = new Transaction();
        mienne.setId(1L);
        mienne.setClientId("SOC-001");
        Transaction autre = new Transaction();
        autre.setId(2L);
        autre.setClientId("SOC-002");
        Anomalie a1 = Anomalie.builder().id(11L).type(AnomalieType.IBAN_INVALIDE).transactionId(1L)
                .statut(AnomalieStatut.OUVERTE).build();
        Anomalie a2 = Anomalie.builder().id(12L).type(AnomalieType.IBAN_INVALIDE).transactionId(2L)
                .statut(AnomalieStatut.OUVERTE).build();
        expertDeSoc001();
        when(transactionRepository.findAll()).thenReturn(List.of(mienne, autre));
        when(anomalieRepository.findAll()).thenReturn(List.of(a1, a2));
        when(recommendationEngine.recommander(any())).thenReturn("reco");

        assertThat(service().lister(null)).extracting(AnomalieDto::id).containsExactly(11L);
    }

    @Test
    void un_expert_ne_peut_pas_changer_le_statut_d_une_anomalie_d_une_autre_societe() {
        Anomalie a = Anomalie.builder().id(12L).type(AnomalieType.IBAN_INVALIDE).transactionId(2L)
                .statut(AnomalieStatut.OUVERTE).build();
        Transaction autre = new Transaction();
        autre.setId(2L);
        autre.setClientId("SOC-002");
        when(anomalieRepository.findById(12L)).thenReturn(Optional.of(a));
        when(transactionRepository.findById(2L)).thenReturn(Optional.of(autre));
        when(acces.autorise("SOC-002")).thenReturn(false);

        assertThatThrownBy(() -> service().changerStatut(12L, "CORRIGEE", ""))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(a.getStatut()).isEqualTo(AnomalieStatut.OUVERTE);
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
