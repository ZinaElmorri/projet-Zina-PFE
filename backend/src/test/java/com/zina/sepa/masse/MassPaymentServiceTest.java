package com.zina.sepa.masse;

import com.zina.sepa.ai.AiAnomalyService;
import com.zina.sepa.ai.dto.AnalyseResult;
import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.entity.XsdSchema;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.dto.ValidationResult;
import com.zina.sepa.masse.dto.MasseDto.ApercuRequest;
import com.zina.sepa.masse.dto.MasseDto.ErreurXsd;
import com.zina.sepa.masse.dto.MasseDto.PaiementsResponse;
import com.zina.sepa.repository.XsdSchemaRepository;
import com.zina.sepa.service.AuditService;
import com.zina.sepa.service.BusinessControlService;
import com.zina.sepa.service.ValidationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

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
    @Mock AiAnomalyService aiAnomalyService;

    BusinessControlService businessControlService = new BusinessControlService();
    ObjectMapper objectMapper = new ObjectMapper();

    private MassPaymentService service() {
        return new MassPaymentService(paiementRepository, xsdSchemaRepository, businessControlService,
                validationService, auditService, xsdReader, generator, errorCollector,
                fichierRepository, objectMapper, aiAnomalyService);
    }

    private Transaction tx() {
        return Transaction.builder().id(1L).employeId("EMP1").nomBeneficiaire("Alice")
                .iban("FR7630006000011234567890189").bic("AGRIFRPP").montant(new BigDecimal("2000"))
                .devise("EUR").statut(TransactionStatus.APPROUVEE).build();
    }

    @ParameterizedTest
    @EnumSource(value = TransactionStatus.class, names = "APPROUVEE", mode = EnumSource.Mode.EXCLUDE)
    void ids_non_approuves_refuses_avant_generation_et_enregistrement(TransactionStatus statut) {
        preparerSchema();
        Transaction paiement = tx();
        paiement.setStatut(statut);
        when(paiementRepository.findAllById(List.of(1L))).thenReturn(List.of(paiement));

        assertThatThrownBy(() -> service().apercu(requete(), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non approuvés");
        assertThatThrownBy(() -> service().enregistrer(requete(), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non approuvés");
        verifyNoInteractions(generator, validationService, errorCollector, fichierRepository, auditService);
        assertThat(paiement.getStatut()).isEqualTo(statut);
    }

    @Test
    void selection_mixte_ne_genere_pas_silencieusement_un_xml_partiel() {
        preparerSchema();
        Transaction approuve = tx();
        Transaction nonApprouve = tx();
        nonApprouve.setId(2L);
        nonApprouve.setEmployeId("EMP2");
        nonApprouve.setStatut(TransactionStatus.BROUILLON);
        when(paiementRepository.findAllById(List.of(1L))).thenReturn(List.of(approuve, nonApprouve));

        assertThatThrownBy(() -> service().apercu(requete(), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("EMP2");
        verifyNoInteractions(generator, fichierRepository, auditService);
    }

    @Test
    void changement_de_statut_apres_apercu_empeche_enregistrement() {
        preparerGeneration(List.of());
        assertThat(service().apercu(requete(), null).valideXsd()).isTrue();
        clearInvocations(generator, auditService);
        Transaction paiement = tx();
        paiement.setStatut(TransactionStatus.BROUILLON);
        when(paiementRepository.findAllById(List.of(1L))).thenReturn(List.of(paiement));

        assertThatThrownBy(() -> service().enregistrer(requete(), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("non approuvés");
        verifyNoInteractions(generator, fichierRepository, auditService);
    }

    @Test
    @SuppressWarnings("unchecked")
    void statut_validee_ne_peut_pas_elargir_la_recherche_aux_non_approuves() {
        when(paiementRepository.filtrer(any(), any(), any(), any(), anyBoolean(), any())).thenReturn(List.of());
        service().paiements("CLI1", "2026-08", List.of(), List.of("VALIDEE", "BROUILLON"));
        ArgumentCaptor<Collection<TransactionStatus>> statuts = ArgumentCaptor.forClass(Collection.class);
        verify(paiementRepository).filtrer(statuts.capture(), any(), any(), any(), anyBoolean(), any());
        assertThat(statuts.getValue()).containsExactly(TransactionStatus.APPROUVEE);
    }

    @Test
    void avertissement_optionnel_ne_modifie_pas_le_verdict_xsd() {
        preparerGeneration(List.of());
        ErreurXsd avertissement = new ErreurXsd(3, 0, "Balise optionnelle vide omise", "Dbtr");
        when(generator.generer(any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new MassPaymentXmlGenerator.Genere("<Document/>", "MSG1", 1,
                        new BigDecimal("2000"), List.of(avertissement), List.of()));

        var apercu = service().apercu(requete(), null);

        assertThat(apercu.valideXsd()).isTrue();
        assertThat(apercu.erreurs()).isEmpty();
        assertThat(apercu.avertissements()).containsExactly(avertissement);
    }

    @Test
    void erreur_iban_metier_est_positionnee_sur_la_balise_du_beneficiaire() {
        preparerGeneration(List.of());
        Transaction transaction = tx();
        transaction.setIban("FR7612345678901234567890123");
        when(paiementRepository.findAllById(List.of(1L))).thenReturn(List.of(transaction));
        when(generator.generer(any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new MassPaymentXmlGenerator.Genere("<Document/>", "MSG1", 1,
                        new BigDecimal("2000"), List.of(),
                        List.of(new MassPaymentXmlGenerator.ReperePaiement("EMP1", 4, 7, 5, 6))));

        var apercu = service().apercu(requete(), null);

        assertThat(apercu.erreursControleMetierXml()).hasSize(1);
        assertThat(apercu.erreursControleMetierXml().get(0).ligne()).isEqualTo(7);
        assertThat(apercu.erreursControleMetierXml().get(0).element()).isEqualTo("IBAN");
    }

            private ApercuRequest requete() {
            return new ApercuRequest(1L, "CLI1", "2026-08", List.of(), List.of(1L),
                "Societe", "FR7630001007941234567890185", "BNPAFRPP", "2026-10-07",
                List.of("APPROUVEE"), false);
            }

            private void preparerSchema() {
            XsdSchema schema = XsdSchema.builder().id(1L).nom("pain.001.xsd").contenu("schema").build();
            when(xsdSchemaRepository.findById(1L)).thenReturn(Optional.of(schema));
            when(xsdReader.meta("schema")).thenReturn(new XsdPain001Reader.MetaXsd("001.001.03", "namespace", true));
            }

            private void preparerGeneration(List<ErreurXsd> erreurs) {
            preparerSchema();
            when(paiementRepository.findAllById(List.of(1L))).thenReturn(List.of(tx()));
            when(generator.generer(any(), any(), any(), any(), any(), any(), any(), any(), org.mockito.ArgumentMatchers.isNull()))
                .thenReturn(new MassPaymentXmlGenerator.Genere("<Document/>", "MSG1", 1, new BigDecimal("2000")));
            when(validationService.validerSyntaxe("<Document/>"))
                .thenReturn(new ValidationResult(true, List.of(), List.of()));
            when(errorCollector.valider("<Document/>", "schema")).thenReturn(erreurs);
            }

            @Test
            void apercu_invalide_reste_disponible_sans_enregistrement() {
            List<ErreurXsd> erreurs = List.of(new ErreurXsd(1, 1, "Element invalide", "Document"));
            preparerGeneration(erreurs);

            var apercu = service().apercu(requete(), null);

            assertThat(apercu.valideXsd()).isFalse();
            assertThat(apercu.erreurs()).isEqualTo(erreurs);
            assertThat(apercu.xml()).isEqualTo("<Document/>");
            verifyNoInteractions(fichierRepository);
            }

        @Test
        void erreurs_et_avertissements_metier_sont_distincts_du_verdict_xsd() {
            preparerGeneration(List.of());
            Transaction valide = tx();
            Transaction doublon = tx();
            doublon.setId(2L);
            doublon.setEmployeId("EMP2");
            Transaction invalide = tx();
            invalide.setId(3L);
            invalide.setEmployeId("EMP3");
            invalide.setIban("FR7612345678901234567890123");
            when(paiementRepository.findAllById(List.of(1L))).thenReturn(List.of(valide, doublon, invalide));

            var apercu = service().apercu(requete(), null);

            assertThat(apercu.valideXsd()).isTrue();
            assertThat(apercu.erreursControleMetier()).hasSize(1)
                    .allMatch(erreur -> erreur.contains("EMP3") && erreur.contains("IBAN invalide"));
            assertThat(apercu.avertissementsControleMetier()).hasSize(1)
                    .allMatch(avertissement -> avertissement.contains("EMP2") && avertissement.contains("Doublon"));
        }

        @Test
        void montant_suspect_ia_ajoute_un_avertissement_non_bloquant() {
            preparerGeneration(List.of());
            Transaction suspect = tx();
            suspect.setSourceAnomalie("IA");
            suspect.setScoreRisque(0.82);
            suspect.setRaisonsAnomalie("Montant 3,7x la moyenne");
            when(paiementRepository.findAllById(List.of(1L))).thenReturn(List.of(suspect));

            var apercu = service().apercu(requete(), null);

            assertThat(apercu.valideXsd()).isTrue();
            assertThat(apercu.erreursControleMetier()).isEmpty();
            assertThat(apercu.avertissementsControleMetier()).singleElement().satisfies(a -> assertThat(a)
                    .startsWith("[emp EMP1]").contains("Montant suspect (IA, score 0,82)").contains("3,7x"));
        }

    @Test
    void analyse_ia_signale_les_suspects_de_la_selection() {
        Transaction normal = tx();
        Transaction suspect = tx();
        suspect.setId(2L);
        suspect.setEmployeId("EMP2");
        when(paiementRepository.filtrer(any(), any(), any(), any(), anyBoolean(), any()))
                .thenReturn(List.of(normal, suspect));
        when(aiAnomalyService.analyser(List.of(normal, suspect))).thenAnswer(invocation -> {
            suspect.setSourceAnomalie("IA");
            suspect.setScoreRisque(0.9);
            suspect.setRaisonsAnomalie("IBAN modifié récemment");
            return new AnalyseResult(true, "iforest-test", List.of());
        });

        var analyse = service().analyserIa("CLI1", "2026-08", List.of(), null);

        assertThat(analyse.disponible()).isTrue();
        assertThat(analyse.nbAnalyses()).isEqualTo(2);
        assertThat(analyse.nbSuspects()).isEqualTo(1);
        assertThat(analyse.paiements().paiements())
                .filteredOn(p -> p.suspectIa())
                .singleElement()
                .satisfies(p -> {
                    assertThat(p.matricule()).isEqualTo("EMP2");
                    assertThat(p.scoreIa()).isEqualTo(0.9);
                    assertThat(p.raisonsIa()).contains("IBAN");
                });
    }

            @Test
            void enregistrement_invalide_refuse_avant_persistance_et_audit() {
            preparerGeneration(List.of(new ErreurXsd(1, 1, "Element invalide", "Document")));

            assertThatThrownBy(() -> service().enregistrer(requete(), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("enregistrement interdit");

            verifyNoInteractions(fichierRepository, auditService);
            }

        @Test
        void erreur_de_syntaxe_detaillee_n_est_pas_doublee_par_un_message_generique() {
            ErreurXsd erreur = new ErreurXsd(3, 12, "XML mal forme", "Document");
            preparerGeneration(List.of(erreur));
            when(validationService.validerSyntaxe("<Document/>"))
                    .thenReturn(new ValidationResult(false, List.of("XML mal forme"), List.of()));

            var apercu = service().apercu(requete(), null);

            assertThat(apercu.valideXsd()).isFalse();
            assertThat(apercu.erreurs()).containsExactly(erreur);
        }

        @Test
        void syntaxe_invalide_reste_bloquante_meme_si_le_collecteur_ne_retourne_rien() {
            preparerGeneration(List.of());
            when(validationService.validerSyntaxe("<Document/>"))
                    .thenReturn(new ValidationResult(false, List.of("XML mal forme"), List.of()));

            var apercu = service().apercu(requete(), null);

            assertThat(apercu.valideXsd()).isFalse();
            assertThat(apercu.erreurs()).hasSize(1);
        }

            @Test
            void enregistrement_revalide_meme_apres_un_apercu_valide() {
            preparerGeneration(List.of());
            assertThat(service().apercu(requete(), null).valideXsd()).isTrue();
            clearInvocations(auditService);
            when(errorCollector.valider("<Document/>", "schema"))
                .thenReturn(List.of(new ErreurXsd(1, 1, "Schema modifie", "Document")));

            assertThatThrownBy(() -> service().enregistrer(requete(), null))
                .isInstanceOf(IllegalStateException.class);

            verifyNoInteractions(fichierRepository, auditService);
            }

            @Test
            void enregistrement_valide_conserve_le_xml_et_sa_conformite() {
            preparerGeneration(List.of());
            when(fichierRepository.save(any(FichierSepaGenere.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

            FichierSepaGenere fichier = service().enregistrer(requete(), null);

            assertThat(fichier.isValideXsd()).isTrue();
            assertThat(fichier.getContenuXml()).isEqualTo("<Document/>");
            assertThat(fichier.getNbTransactions()).isEqualTo(1);
            verify(fichierRepository).save(fichier);
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

    @Test
    void fichier_invalide_consultable_mais_non_telechargeable() {
        FichierSepaGenere fichier = FichierSepaGenere.builder().id(8L)
                .contenuXml("<Document>invalid</Document>").valideXsd(false).erreursXsd("[]").build();
        when(fichierRepository.findById(8L)).thenReturn(Optional.of(fichier));

        assertThatThrownBy(() -> service().pourTelecharger(8L, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("téléchargement interdit");
        assertThat(service().detail(8L).xml()).isEqualTo("<Document>invalid</Document>");
        verifyNoInteractions(auditService);
    }
}
