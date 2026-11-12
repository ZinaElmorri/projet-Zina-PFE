package com.zina.sepa.societe;

import com.zina.sepa.domain.entity.Societe;
import com.zina.sepa.repository.SocieteRepository;
import com.zina.sepa.service.BusinessControlService;
import com.zina.sepa.societe.SocieteService.SocieteRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SocieteServiceTest {

    @Mock SocieteRepository repository;

    private SocieteService service() {
        return new SocieteService(repository, new BusinessControlService());
    }

    @Test
    void iban_invalide_refuse_sans_enregistrement() {
        assertThatThrownBy(() -> service().creer(new SocieteRequest("SOC-9", "Test", "FR7612345678901234567890123", "AGRIFRPP", true)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("IBAN");
        verify(repository, never()).save(any());
    }

    @Test
    void reponse_ne_contient_que_l_iban_masque() {
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service().creer(new SocieteRequest("SOC-9", "Test", "fr76 3000 6000 0112 3456 7890 189", "agrifrpp", true));

        assertThat(dto.ibanMasque()).isEqualTo("FR76 **** **** **** **** 0189");
        assertThat(dto.bic()).isEqualTo("AGRIFRPP");
    }

    @Test
    void modification_sans_iban_conserve_l_iban_existant() {
        Societe existante = Societe.builder().id(3L).code("SOC-1").nom("Ancienne").iban("FR7630006000011234567890189")
                .bic("AGRIFRPP").actif(true).build();
        when(repository.findById(3L)).thenReturn(Optional.of(existante));
        when(repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var dto = service().modifier(3L, new SocieteRequest("SOC-1", "Nouveau nom", "", "AGRIFRPP", true));

        assertThat(dto.nom()).isEqualTo("Nouveau nom");
        assertThat(dto.ibanMasque()).isEqualTo("FR76 **** **** **** **** 0189");
    }

    @Test
    void societe_inactive_ne_fournit_pas_de_debiteur() {
        Societe inactive = Societe.builder().code("SOC-1").nom("Ancienne").iban("FR7630006000011234567890189")
                .bic("AGRIFRPP").actif(false).build();
        when(repository.findByCode("SOC-1")).thenReturn(Optional.of(inactive));

        assertThat(service().debiteurPour("SOC-1")).isEmpty();
    }
}
