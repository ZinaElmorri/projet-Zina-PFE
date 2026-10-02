package com.zina.sepa.masse;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.masse.dto.MasseDto.PaiementsResponse;
import com.zina.sepa.repository.XsdSchemaRepository;
import com.zina.sepa.service.AuditService;
import com.zina.sepa.service.BusinessControlService;
import com.zina.sepa.service.ValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MassPaymentServiceTest {

    @Mock MassePaiementRepository paiementRepository;
    @Mock XsdSchemaRepository xsdSchemaRepository;
    @Mock ValidationService validationService;
    @Mock AuditService auditService;
    @Mock XsdPain001Reader xsdReader;
    @Mock MassPaymentXmlGenerator generator;
    @Mock XsdErrorCollector errorCollector;
    @Mock FichierSepaGenereRepository fichierRepository;

    BusinessControlService businessControlService = new BusinessControlService();
    ObjectMapper objectMapper = new ObjectMapper();

    private MassPaymentService service() {
        return new MassPaymentService(paiementRepository, xsdSchemaRepository, businessControlService,
                validationService, auditService, xsdReader, generator, errorCollector,
                fichierRepository, objectMapper);
    }

    private Transaction tx() {
        return Transaction.builder().id(1L).employeId("EMP1").nomBeneficiaire("Alice")
                .iban("FR7630006000011234567890189").bic("AGRIFRPP").montant(new BigDecimal("2000"))
                .devise("EUR").statut(TransactionStatus.APPROUVEE).build();
    }

    @Test
    void matricules_vides_selectionnent_tous_les_salaries() {
        ArgumentCaptor<Boolean> sansMat = ArgumentCaptor.forClass(Boolean.class);
        when(paiementRepository.filtrer(any(), any(), any(), any(), sansMat.capture(), any()))
                .thenReturn(List.of(tx()));

        PaiementsResponse r = service().paiements("CLI1", "2026-08", List.of(), null);

        assertThat(sansMat.getValue()).isTrue();
        assertThat(r.nombre()).isEqualTo(1);
        assertThat(r.total()).isEqualByComparingTo("2000");
    }

    @Test
    void matricules_precises_filtrent_sur_ces_matricules() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<String>> mats = ArgumentCaptor.forClass(Collection.class);
        ArgumentCaptor<Boolean> sansMat = ArgumentCaptor.forClass(Boolean.class);
        when(paiementRepository.filtrer(any(), any(LocalDateTime.class), any(LocalDateTime.class),
                any(), sansMat.capture(), mats.capture())).thenReturn(List.of(tx()));

        service().paiements("CLI1", "2026-08", List.of("EMP1"), List.of("APPROUVEE"));

        assertThat(sansMat.getValue()).isFalse();
        assertThat(mats.getValue()).contains("EMP1");
    }

    @Test
    void statut_par_defaut_est_approuvee() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<TransactionStatus>> statuts = ArgumentCaptor.forClass(Collection.class);
        when(paiementRepository.filtrer(statuts.capture(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(List.of());

        service().paiements("CLI1", "2026-08", List.of(), null);

        assertThat(statuts.getValue()).containsExactly(TransactionStatus.APPROUVEE);
    }

    @Test
    void retelechargement_renvoie_le_meme_contenu() {
        FichierSepaGenere f = FichierSepaGenere.builder().id(7L).nomFichier("SEPA_CLI1_2026-08_MSG.xml")
                .contenuXml("<Document>same</Document>").valideXsd(true).erreursXsd("[]").build();
        when(fichierRepository.findById(7L)).thenReturn(Optional.of(f));

        FichierSepaGenere t1 = service().pourTelecharger(7L, null);
        var detail = service().detail(7L);

        assertThat(t1.getContenuXml()).isEqualTo("<Document>same</Document>");
        assertThat(detail.xml()).isEqualTo("<Document>same</Document>");
    }
}
