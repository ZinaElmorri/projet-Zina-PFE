package com.zina.sepa.masse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zina.sepa.ai.AiAnomalyService;
import com.zina.sepa.masse.dto.MasseDto.FichierResume;
import com.zina.sepa.repository.XsdSchemaRepository;
import com.zina.sepa.service.AuditService;
import com.zina.sepa.service.BusinessControlService;
import com.zina.sepa.service.ValidationService;
import com.zina.sepa.societe.SocieteService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** L'historique de l'administrateur est une vue de supervision : aucune donnée par salarié. */
@ExtendWith(MockitoExtension.class)
class HistoriqueSupervisionTest {

    @Mock MassePaiementRepository paiementRepository;
    @Mock XsdSchemaRepository xsdSchemaRepository;
    @Mock ValidationService validationService;
    @Mock AuditService auditService;
    @Mock XsdPain001Reader xsdReader;
    @Mock MassPaymentXmlGenerator generator;
    @Mock XsdErrorCollector errorCollector;
    @Mock FichierSepaGenereRepository fichierRepository;
    @Mock AiAnomalyService aiAnomalyService;
    @Mock SocieteService societeService;
    @Mock AccesSocietes acces;

    private MassPaymentService service() {
        return new MassPaymentService(paiementRepository, xsdSchemaRepository, new BusinessControlService(),
                validationService, auditService, xsdReader, generator, errorCollector,
                fichierRepository, new ObjectMapper(), aiAnomalyService, societeService, acces);
    }

    private void unFichier() {
        FichierSepaGenere f = FichierSepaGenere.builder().id(3L).nomFichier("SEPA_SOC-001.xml")
                .contenuXml("<Document/>").clientId("SOC-001").periode("2026-10").matricules("EMP001,EMP002")
                .nbTransactions(2).ctrlSum(new BigDecimal("2780.00")).valideXsd(true).generePar("expert1")
                .genereLe(LocalDateTime.now()).statutFichier("REJETE").soumisPar("expert1")
                .decisionPar("manager1").motifRejet("EMP002 : IBAN invalide").nbSuspectsIa(1).build();
        when(fichierRepository.findAllByOrderByGenereLeDesc()).thenReturn(List.of(f));
        when(acces.autorise("SOC-001")).thenReturn(true);
    }

    @Test
    void l_admin_ne_recoit_que_les_informations_de_supervision() {
        unFichier();
        when(acces.estAdmin()).thenReturn(true);

        FichierResume r = service().historique(null, null, null, null).get(0);

        assertThat(r.clientId()).isEqualTo("SOC-001");
        assertThat(r.periode()).isEqualTo("2026-10");
        assertThat(r.nbTransactions()).isEqualTo(2);
        assertThat(r.ctrlSum()).isEqualByComparingTo("2780.00");
        assertThat(r.statutFichier()).isEqualTo("REJETE");
        assertThat(r.nbSuspectsIa()).isEqualTo(1);
        assertThat(r.generePar()).isEqualTo("expert1");
        assertThat(r.matricules()).isNull();
        assertThat(r.motifRejet()).isNull();
        assertThat(r.soumisPar()).isNull();
        assertThat(r.decisionPar()).isNull();
    }

    @Test
    void les_autres_roles_gardent_l_historique_complet() {
        unFichier();
        when(acces.estAdmin()).thenReturn(false);

        FichierResume r = service().historique(null, null, null, null).get(0);

        assertThat(r.matricules()).isEqualTo("EMP001,EMP002");
        assertThat(r.motifRejet()).isEqualTo("EMP002 : IBAN invalide");
        assertThat(r.soumisPar()).isEqualTo("expert1");
        assertThat(r.decisionPar()).isEqualTo("manager1");
    }
}
