package com.zina.sepa.security;

import com.zina.sepa.affectation.AffectationController;
import com.zina.sepa.affectation.AffectationService;
import com.zina.sepa.controller.AuditController;
import com.zina.sepa.controller.TransactionController;
import com.zina.sepa.controller.UserController;
import com.zina.sepa.controller.XsdController;
import com.zina.sepa.masse.FichierWorkflowService;
import com.zina.sepa.masse.MassController;
import com.zina.sepa.masse.MassPaymentService;
import com.zina.sepa.repository.TransactionRepository;
import com.zina.sepa.service.AuditService;
import com.zina.sepa.service.BusinessControlService;
import com.zina.sepa.service.UserService;
import com.zina.sepa.service.XmlGenerationService;
import com.zina.sepa.service.XsdService;
import com.zina.sepa.societe.SocieteController;
import com.zina.sepa.societe.SocieteService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Pageable;
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

/** Vérifie la matrice d'accès par rôle sur les contrôleurs protégés par les constantes de {@link Roles}. */
@SpringJUnitConfig(RolesAccessTest.Config.class)
class RolesAccessTest {

    @Configuration
    @EnableMethodSecurity
    static class Config {
        @Bean AuditController audit() { return new AuditController(mock(AuditService.class)); }
        @Bean UserController users() { return new UserController(mock(UserService.class)); }
        @Bean SocieteController societes() { return new SocieteController(mock(SocieteService.class)); }
        @Bean XsdController xsd() { return new XsdController(mock(XsdService.class), mock(AuditService.class)); }
        @Bean AffectationController affectations() { return new AffectationController(mock(AffectationService.class)); }
        @Bean MassController masse() { return new MassController(mock(MassPaymentService.class), mock(FichierWorkflowService.class)); }
        @Bean TransactionController transactions() {
            return new TransactionController(mock(TransactionRepository.class), new BusinessControlService(),
                    mock(XmlGenerationService.class), mock(AuditService.class));
        }
    }

    @Autowired AuditController audit;
    @Autowired UserController users;
    @Autowired SocieteController societes;
    @Autowired XsdController xsd;
    @Autowired AffectationController affectations;
    @Autowired MassController masse;
    @Autowired TransactionController transactions;

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
    void journal_d_audit_reserve_a_l_admin() {
        connecte("ADMIN");
        assertThat(autorise(() -> audit.list(Pageable.unpaged()))).isTrue();
        connecte("MANAGER");
        assertThat(autorise(() -> audit.list(Pageable.unpaged()))).isFalse();
        connecte("EXPERT_RH");
        assertThat(autorise(() -> audit.list(Pageable.unpaged()))).isFalse();
    }

    @Test
    void administration_reservee_a_l_admin() {
        for (String role : List.of("MANAGER", "EXPERT_RH")) {
            connecte(role);
            assertThat(autorise(() -> users.list())).as("utilisateurs " + role).isFalse();
            assertThat(autorise(() -> societes.creer(null))).as("sociétés " + role).isFalse();
        }
        connecte("EXPERT_RH");
        assertThat(autorise(() -> xsd.supprimer(1L, null))).as("xsd expert").isFalse();
        for (String role : List.of("ADMIN", "MANAGER")) {
            connecte(role);
            assertThat(autorise(() -> xsd.supprimer(1L, null))).as("xsd " + role).isTrue();
        }
        connecte("ADMIN");
        assertThat(autorise(() -> users.list())).isTrue();
        assertThat(autorise(() -> societes.creer(null))).isTrue();
    }

    @Test
    void preparation_et_soumission_reservees_a_l_expert_rh() {
        connecte("EXPERT_RH");
        assertThat(autorise(() -> masse.controler(null, null))).isTrue();
        assertThat(autorise(() -> masse.soumettre(1L, null))).isTrue();
        assertThat(autorise(() -> transactions.create(null, null))).isTrue();
        for (String role : List.of("ADMIN", "MANAGER")) {
            connecte(role);
            assertThat(autorise(() -> masse.controler(null, null))).as("contrôle " + role).isFalse();
            assertThat(autorise(() -> masse.apercu(null, null))).as("aperçu " + role).isFalse();
            assertThat(autorise(() -> masse.soumettre(1L, null))).as("soumission " + role).isFalse();
            assertThat(autorise(() -> transactions.create(null, null))).as("transaction " + role).isFalse();
        }
    }

    @Test
    void validation_et_affectation_reservees_au_manager() {
        connecte("MANAGER");
        assertThat(autorise(() -> masse.valider(1L, null))).isTrue();
        assertThat(autorise(() -> masse.rejeter(1L, new MassController.RejetRequest("motif"), null))).isTrue();
        assertThat(autorise(() -> affectations.lister())).isTrue();
        assertThat(autorise(() -> transactions.approuver(1L, null))).isTrue();
        for (String role : List.of("ADMIN", "EXPERT_RH")) {
            connecte(role);
            assertThat(autorise(() -> masse.valider(1L, null))).as("validation " + role).isFalse();
            assertThat(autorise(() -> masse.rejeter(1L, new MassController.RejetRequest("m"), null))).as("rejet " + role).isFalse();
            assertThat(autorise(() -> affectations.lister())).as("affectations " + role).isFalse();
            assertThat(autorise(() -> transactions.approuver(1L, null))).as("approbation " + role).isFalse();
        }
    }

    @Test
    void consultation_ouverte_aux_trois_roles_et_refusee_aux_anciens_roles() {
        for (String role : List.of("ADMIN", "MANAGER", "EXPERT_RH")) {
            connecte(role);
            assertThat(autorise(() -> masse.historique(null, null, null, null))).as("historique " + role).isTrue();
            assertThat(autorise(() -> societes.lister())).as("sociétés " + role).isTrue();
        }
        for (String ancien : List.of("ADMINISTRATEUR", "GESTIONNAIRE_RH", "VALIDATEUR", "AUDITEUR")) {
            connecte(ancien);
            assertThat(autorise(() -> masse.historique(null, null, null, null))).as("ancien rôle " + ancien).isFalse();
        }
    }
}
