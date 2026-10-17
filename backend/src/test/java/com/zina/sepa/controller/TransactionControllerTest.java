package com.zina.sepa.controller;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.repository.TransactionRepository;
import com.zina.sepa.service.AuditService;
import com.zina.sepa.service.BusinessControlService;
import com.zina.sepa.service.XmlGenerationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionControllerTest {

    @Mock TransactionRepository repository;
    @Mock XmlGenerationService xmlGenerationService;
    @Mock AuditService auditService;

    private TransactionController controller() {
        return new TransactionController(repository, new BusinessControlService(), xmlGenerationService,
                auditService);
    }

    private Transaction paiement() {
        return Transaction.builder().id(1L).employeId("EMP1").nomBeneficiaire("Alice")
                .iban("FR7630006000011234567890189").bic("AGRIFRPP")
                .montant(new BigDecimal("100.00")).statut(TransactionStatus.BROUILLON).build();
    }

    private void verifierRefus(Transaction paiement, String champ) {
        when(repository.findById(1L)).thenReturn(Optional.of(paiement));
        assertThatThrownBy(() -> controller().approuver(1L, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining(champ);
        assertThat(paiement.getStatut()).isEqualTo(TransactionStatus.BROUILLON);
        verify(repository, never()).save(any());
        verifyNoInteractions(auditService, xmlGenerationService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "INVALID", "FR7630006000011234567890188"})
    void iban_invalide_empeche_approbation_sans_mutation(String iban) {
        Transaction paiement = paiement();
        paiement.setIban(iban);
        verifierRefus(paiement, "IBAN");
    }

    @Test
    void bic_invalide_empeche_approbation() {
        Transaction paiement = paiement();
        paiement.setBic("BAD!");
        verifierRefus(paiement, "BIC");
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void montant_non_positif_empeche_approbation(String montant) {
        Transaction paiement = paiement();
        paiement.setMontant(new BigDecimal(montant));
        verifierRefus(paiement, "Montant");
    }

    @Test
    void montant_manquant_empeche_approbation() {
        Transaction paiement = paiement();
        paiement.setMontant(null);
        verifierRefus(paiement, "Montant");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"AGRIFRPP"})
    void paiement_valide_est_approuve_et_audite_meme_si_bic_optionnel_absent(String bic) {
        Transaction paiement = paiement();
        paiement.setBic(bic);
        when(repository.findById(1L)).thenReturn(Optional.of(paiement));
        assertThat(controller().approuver(1L, null).getBody()).isSameAs(paiement);
        assertThat(paiement.getStatut()).isEqualTo(TransactionStatus.APPROUVEE);
        verify(repository).save(paiement);
        verify(auditService).log(eq(AuditAction.TRANSACTION_APPROUVEE), anyString(), eq("SUCCES"), isNull());
    }
}