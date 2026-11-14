package com.zina.sepa.security;

import com.zina.sepa.ai.AiAnomalyService;
import com.zina.sepa.ai.AiDetectionController;
import com.zina.sepa.controller.TransactionController;
import com.zina.sepa.repository.TransactionRepository;
import com.zina.sepa.service.AuditService;
import com.zina.sepa.service.BusinessControlService;
import com.zina.sepa.service.XmlGenerationService;
import com.zina.sepa.recommendation.AnomalieController;
import com.zina.sepa.recommendation.AnomalieService;
import com.zina.sepa.masse.FichierWorkflowService;
import com.zina.sepa.masse.MassController;
import com.zina.sepa.masse.MassPaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * L'administrateur gère le système mais n'accède ni aux données de paie ni aux actions métier ;
 * les droits du Manager et de l'Expert RH restent ceux d'avant.
 */
@SpringJUnitConfig(AdminRestrictionsTest.Config.class)
class AdminRestrictionsTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean TransactionController transactions() {
            return new TransactionController(mock(TransactionRepository.class), new BusinessControlService(),
                    mock(XmlGenerationService.class), mock(AuditService.class));
        }
        @Bean AiDetectionController aiDetection() {
            return new AiDetectionController(mock(AiAnomalyService.class), mock(TransactionRepository.class),
                    mock(AuditService.class));
        }
        @Bean AnomalieController anomalies() { return new AnomalieController(mock(AnomalieService.class)); }
        @Bean MassController masse() { return new MassController(mock(MassPaymentService.class), mock(FichierWorkflowService.class)); }
    }

    @Autowired TransactionController transactions;
    @Autowired AiDetectionController aiDetection;
    @Autowired AnomalieController anomalies;
    @Autowired MassController masse;

    @AfterEach
    void deconnecter() {
        SecurityContextHolder.clearContext();
    }

    private void connecte(String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "test", "x", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    /** Vrai si l'appel passe la sécurité (une autre exception métier ne compte pas comme un refus). */
    private boolean autorise(Callable<?> appel) {
        try {
            appel.call();
            return true;
        } catch (AccessDeniedException e) {
            return false;
        } catch (Exception e) {
            return true;
        }
    }

    @Test
    void transactions_fermees_a_l_admin_ouvertes_aux_autres_roles() {
        connecte("ADMIN");
        assertThat(autorise(() -> transactions.list())).as("liste").isFalse();
        assertThat(autorise(() -> transactions.controler(null))).as("contrôle").isFalse();
        assertThat(autorise(() -> aiDetection.detect(null))).as("détection IA").isFalse();
        for (String role : List.of("MANAGER", "EXPERT_RH")) {
            connecte(role);
            assertThat(autorise(() -> transactions.list())).as("liste " + role).isTrue();
            assertThat(autorise(() -> transactions.controler(null))).as("contrôle " + role).isTrue();
            assertThat(autorise(() -> aiDetection.detect(null))).as("détection IA " + role).isTrue();
        }
    }

    @Test
    void masse_ferme_a_l_admin_sauf_l_historique() {
        connecte("ADMIN");
        assertThat(autorise(() -> masse.historique(null, null, null, null))).as("historique").isTrue();
        assertThat(autorise(() -> masse.filtres(null, null))).as("filtres").isFalse();
        assertThat(autorise(() -> masse.paiements(null, null, null, null))).as("paiements").isFalse();
        assertThat(autorise(() -> masse.analyseIa(null, null, null, null))).as("analyse IA").isFalse();
        assertThat(autorise(() -> masse.xsd())).as("xsd").isFalse();
        assertThat(autorise(() -> masse.analyse(1L))).as("analyse xsd").isFalse();
        assertThat(autorise(() -> masse.detail(1L))).as("détail").isFalse();
        assertThat(autorise(() -> masse.resultat(1L))).as("résultat").isFalse();
        assertThat(autorise(() -> masse.telecharger(1L, null))).as("téléchargement").isFalse();
        assertThat(autorise(() -> masse.apercu(null, null))).as("aperçu").isFalse();
        assertThat(autorise(() -> masse.enregistrer(null, null))).as("génération").isFalse();
        assertThat(autorise(() -> masse.controler(null, null))).as("contrôle").isFalse();
        for (String role : List.of("MANAGER", "EXPERT_RH")) {
            connecte(role);
            assertThat(autorise(() -> masse.historique(null, null, null, null))).as("historique " + role).isTrue();
            assertThat(autorise(() -> masse.filtres(null, null))).as("filtres " + role).isTrue();
            assertThat(autorise(() -> masse.paiements(null, null, null, null))).as("paiements " + role).isTrue();
            assertThat(autorise(() -> masse.analyseIa(null, null, null, null))).as("analyse IA " + role).isTrue();
            assertThat(autorise(() -> masse.xsd())).as("xsd " + role).isTrue();
            assertThat(autorise(() -> masse.analyse(1L))).as("analyse xsd " + role).isTrue();
            assertThat(autorise(() -> masse.detail(1L))).as("détail " + role).isTrue();
            assertThat(autorise(() -> masse.resultat(1L))).as("résultat " + role).isTrue();
            assertThat(autorise(() -> masse.telecharger(1L, null))).as("téléchargement " + role).isTrue();
        }
    }

    @Test
    void anomalies_fermees_a_l_admin_ouvertes_aux_autres_roles() {
        connecte("ADMIN");
        assertThat(autorise(() -> anomalies.lister(null))).as("liste").isFalse();
        assertThat(autorise(() -> anomalies.detecter())).as("détection").isFalse();
        assertThat(autorise(() -> anomalies.changerStatut(1L, null))).as("statut").isFalse();
        for (String role : List.of("MANAGER", "EXPERT_RH")) {
            connecte(role);
            assertThat(autorise(() -> anomalies.lister(null))).as("liste " + role).isTrue();
            assertThat(autorise(() -> anomalies.detecter())).as("détection " + role).isTrue();
            assertThat(autorise(() -> anomalies.changerStatut(1L, null))).as("statut " + role).isTrue();
        }
    }
}
