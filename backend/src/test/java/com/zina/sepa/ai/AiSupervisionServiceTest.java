package com.zina.sepa.ai;

import com.zina.sepa.ai.dto.TrainResponse;
import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.recommendation.Anomalie;
import com.zina.sepa.recommendation.AnomalieRepository;
import com.zina.sepa.recommendation.AnomalieStatut;
import com.zina.sepa.recommendation.AnomalieType;
import com.zina.sepa.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSupervisionServiceTest {

    @Mock MontantSuspectClient client;
    @Mock TransactionRepository transactionRepository;
    @Mock AnomalieRepository anomalieRepository;

    AiProperties properties = new AiProperties();

    private AiSupervisionService service() {
        properties.setEnabled(true);
        AiAnomalyService ai = new AiAnomalyService(properties, client, transactionRepository);
        return new AiSupervisionService(properties, client, ai, anomalieRepository);
    }

    private List<Transaction> transactions(int n) {
        return LongStream.rangeClosed(1, n).mapToObj(i -> Transaction.builder()
                .id(i).employeId("EMP" + i).iban("FR76" + i).montant(new BigDecimal("2000")).build()).toList();
    }

    @Test
    void precision_observee_ne_compte_que_les_retours_utilisateurs() {
        when(anomalieRepository.countByTypeAndStatutAndCorrigeeParIsNotNull(AnomalieType.MONTANT_SUSPECT,
                AnomalieStatut.CORRIGEE)).thenReturn(3L);
        when(anomalieRepository.countByTypeAndStatut(AnomalieType.MONTANT_SUSPECT, AnomalieStatut.IGNOREE)).thenReturn(1L);
        when(anomalieRepository.countByTypeAndStatut(AnomalieType.MONTANT_SUSPECT, AnomalieStatut.OUVERTE)).thenReturn(5L);

        var retours = service().retours();

        assertThat(retours).containsEntry("confirmees", 3L).containsEntry("ignorees", 1L)
                .containsEntry("enAttente", 5L).containsEntry("precisionObservee", 0.75);
    }

    @Test
    void sans_retour_la_precision_observee_est_inconnue() {
        assertThat(service().retours()).containsEntry("precisionObservee", null);
    }

    @Test
    void reentrainement_reel_exclut_les_anomalies_confirmees() {
        List<Transaction> lot = transactions(120);
        when(transactionRepository.findAll()).thenReturn(lot);
        when(transactionRepository.findByEmployeIdIn(any())).thenReturn(lot);
        when(anomalieRepository.findByTypeAndStatutAndCorrigeeParIsNotNull(AnomalieType.MONTANT_SUSPECT,
                AnomalieStatut.CORRIGEE)).thenReturn(List.of(Anomalie.builder().transactionId(7L).build()));
        when(client.train(any())).thenReturn(new TrainResponse("iforest-reel", "ok"));
        ArgumentCaptor<com.zina.sepa.ai.dto.MontantSuspectRequest> requete =
                ArgumentCaptor.forClass(com.zina.sepa.ai.dto.MontantSuspectRequest.class);

        TrainResponse reponse = service().reentrainer("reel");

        verify(client).train(requete.capture());
        assertThat(reponse.modelVersion()).isEqualTo("iforest-reel");
        assertThat(requete.getValue().paiements()).hasSize(119)
                .noneMatch(p -> p.ficheId().equals(7L));
    }

    @Test
    void reentrainement_reel_refuse_si_trop_peu_de_paiements() {
        when(transactionRepository.findAll()).thenReturn(transactions(50));

        assertThatThrownBy(() -> service().reentrainer("reel"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("au moins 100");
        verify(client, never()).train(any());
    }

    @Test
    void source_inconnue_refusee() {
        assertThatThrownBy(() -> service().reentrainer("autre")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reentrainement_synthetique_par_defaut() {
        when(client.train(null)).thenReturn(new TrainResponse("iforest-synth", "ok"));
        assertThat(service().reentrainer(null).modelVersion()).isEqualTo("iforest-synth");
        verify(anomalieRepository, never()).findByTypeAndStatutAndCorrigeeParIsNotNull(any(), any());
    }
}
