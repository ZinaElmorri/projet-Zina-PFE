package com.zina.sepa.ai;

import com.zina.sepa.ai.dto.AnalyseResult;
import com.zina.sepa.ai.dto.MontantSuspectResponse;
import com.zina.sepa.ai.dto.ResultatDto;
import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiAnomalyServiceTest {

    @Mock
    MontantSuspectClient client;
    @Mock
    TransactionRepository repository;

    AiProperties properties = new AiProperties();

    Transaction transaction() {
        Transaction t = new Transaction();
        t.setId(101L);
        t.setEmployeId("EMP1");
        t.setIban("FR7630006000011234567890189");
        t.setMontant(new BigDecimal("7500.00"));
        return t;
    }

    private AiAnomalyService service() {
        return new AiAnomalyService(properties, client, repository);
    }

    @Test
    void ia_desactivee_ne_fait_aucun_appel() {
        properties.setEnabled(false);
        AnalyseResult result = service().analyser(List.of(transaction()));

        assertThat(result.disponible()).isFalse();
        assertThat(result.resultats()).isEmpty();
        verify(client, never()).detect(any());
    }

    @Test
    void ia_ok_marque_la_transaction_comme_montant_suspect() {
        properties.setEnabled(true);
        Transaction t = transaction();
        when(repository.findByEmployeIdIn(any())).thenReturn(List.of(t));
        when(repository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(client.detect(any())).thenReturn(new MontantSuspectResponse(
                "iforest-2026-09-01",
                List.of(new ResultatDto(101L, 0.82, true, List.of("Montant 3,7x la moyenne")))));

        AnalyseResult result = service().analyser(List.of(t));

        assertThat(result.disponible()).isTrue();
        assertThat(result.modelVersion()).isEqualTo("iforest-2026-09-01");
        assertThat(t.getSourceAnomalie()).isEqualTo("IA");
        assertThat(t.getTypeAnomalie()).isEqualTo("MONTANT_SUSPECT");
        assertThat(t.getScoreRisque()).isEqualTo(0.82);
        verify(repository).save(t);
    }

    @Test
    void ia_en_erreur_le_traitement_continue_sans_exception() {
        properties.setEnabled(true);
        Transaction t = transaction();
        when(repository.findByEmployeIdIn(any())).thenReturn(List.of(t));
        when(client.detect(any())).thenThrow(new RuntimeException("timeout"));

        AnalyseResult result = service().analyser(List.of(t));

        assertThat(result.disponible()).isFalse();
        assertThat(result.resultats()).isEmpty();
        assertThat(t.getSourceAnomalie()).isNull();
        verify(repository, never()).save(any());
    }
}
