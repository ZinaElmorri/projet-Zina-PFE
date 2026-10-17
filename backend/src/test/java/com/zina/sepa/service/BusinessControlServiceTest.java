package com.zina.sepa.service;

import org.junit.jupiter.api.Test;
import com.zina.sepa.domain.entity.Transaction;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessControlServiceTest {

    private final BusinessControlService service = new BusinessControlService();

    @Test
    void messages_metier_identifient_le_paiement_et_la_correction() {
        Transaction paiement = Transaction.builder().employeId("EMP1").iban("FR7630006000011234567890188")
                .bic("BAD!").montant(BigDecimal.ZERO).build();
        var resultat = service.controle(List.of(paiement));
        assertThat(resultat.valide()).isFalse();
        assertThat(resultat.erreurs()).hasSize(3).allMatch(message -> message.startsWith("[emp EMP1]"));
        assertThat(resultat.erreurs()).anySatisfy(message -> assertThat(message).contains("clé de contrôle", "relevé bancaire"));
        assertThat(resultat.erreurs()).anySatisfy(message -> assertThat(message).contains("BIC", "8 ou 11 caractères"));
        assertThat(resultat.erreurs()).anySatisfy(message -> assertThat(message).contains("Montant", "strictement supérieur à zéro"));

        paiement.setIban("INVALID");
        assertThat(service.controle(List.of(paiement)).erreurs()).anyMatch(message -> message.contains("format incorrect"));
        paiement.setIban(null);
        paiement.setMontant(null);
        assertThat(service.controle(List.of(paiement)).erreurs()).anyMatch(message -> message.contains("IBAN du bénéficiaire manquant"));
        assertThat(service.controle(List.of(paiement)).erreurs()).anyMatch(message -> message.contains("Montant du virement manquant"));
    }

    @Test
    void doublon_est_un_avertissement_avec_une_action_sans_invalider_les_controles() {
        Transaction paiement = Transaction.builder().employeId("EMP1").iban("FR7630006000011234567890189")
                .bic("AGRIFRPP").montant(new BigDecimal("100.00")).build();
        var resultat = service.controle(List.of(paiement, paiement));
        assertThat(resultat.valide()).isTrue();
        assertThat(resultat.erreurs()).isEmpty();
        assertThat(resultat.avertissements()).singleElement().asString().contains("même IBAN", "même montant", "Vérifiez les paiements");
    }

    @Test
    void iban_emp001_valide_n_est_pas_signale_en_erreur() {
        assertThat(service.ibanValide("FR7630006000011234567890189")).isTrue();
    }

    @Test
    void normalise_les_espaces_et_minuscules_avant_le_modulo_97() {
        assertThat(service.ibanValide("fr76 3000 6000 0112 3456 7890 189")).isTrue();
        assertThat(service.ibanValide("DE89370400440532013000")).isTrue();
    }

    @Test
    void rejette_les_cles_incorrectes_et_les_formats_invalides() {
        assertThat(service.ibanValide("FR7630006000011234567890188")).isFalse();
        assertThat(service.ibanValide("FR7612345678901234567890123")).isFalse();
        assertThat(service.ibanValide("FR!!INVALID")).isFalse();
        assertThat(service.ibanValide("")).isFalse();
        assertThat(service.ibanValide(null)).isFalse();
    }
}