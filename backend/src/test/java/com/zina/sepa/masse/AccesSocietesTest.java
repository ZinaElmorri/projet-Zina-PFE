package com.zina.sepa.masse;

import com.zina.sepa.affectation.AffectationService;
import com.zina.sepa.domain.entity.Transaction;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AccesSocietesTest {

    AffectationService affectations = mock(AffectationService.class);
    AccesSocietes acces = new AccesSocietes(affectations);

    @AfterEach
    void deconnecter() {
        SecurityContextHolder.clearContext();
    }

    private void connecte(String utilisateur, String role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                utilisateur, "x", List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    @Test
    void un_expert_ne_voit_que_ses_societes() {
        when(affectations.societesDe("expert1")).thenReturn(Set.of("SOC-001"));
        connecte("expert1", "EXPERT_RH");

        assertThat(acces.filtrer(List.of("SOC-001", "SOC-002"))).containsExactly("SOC-001");
        assertThat(acces.autorise("SOC-002")).isFalse();
        acces.verifier("SOC-001");
        assertThatThrownBy(() -> acces.verifier("SOC-002")).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> acces.verifier(" ")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void manager_et_admin_voient_toutes_les_societes() {
        for (String role : List.of("MANAGER", "ADMIN")) {
            connecte("u", role);
            assertThat(acces.filtrer(List.of("SOC-001", "SOC-002"))).hasSize(2);
            assertThat(acces.autorise("SOC-002")).isTrue();
            acces.verifier("SOC-002");
        }
    }

    @Test
    void un_expert_ne_voit_que_les_fiches_de_ses_societes_et_jamais_celles_sans_societe() {
        when(affectations.societesDe("expert1")).thenReturn(Set.of("SOC-001"));
        Transaction mienne = new Transaction();
        mienne.setClientId("SOC-001");
        Transaction autre = new Transaction();
        autre.setClientId("SOC-002");
        Transaction sansSociete = new Transaction();
        List<Transaction> toutes = List.of(mienne, autre, sansSociete);

        connecte("expert1", "EXPERT_RH");
        assertThat(acces.filtrerTransactions(toutes)).containsExactly(mienne);

        connecte("manager1", "MANAGER");
        assertThat(acces.filtrerTransactions(toutes)).hasSize(3);
    }
}
