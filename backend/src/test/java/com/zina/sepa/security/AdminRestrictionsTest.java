package com.zina.sepa.security;

import com.zina.sepa.ai.AiAnomalyService;
import com.zina.sepa.ai.AiDetectionController;
import com.zina.sepa.controller.TransactionController;
import com.zina.sepa.repository.TransactionRepository;
import com.zina.sepa.service.AuditService;
import com.zina.sepa.service.BusinessControlService;
import com.zina.sepa.service.XmlGenerationService;
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
    }

    @Autowired TransactionController transactions;
    @Autowired AiDetectionController aiDetection;

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
}
