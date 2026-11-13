package com.zina.sepa.affectation;

import com.zina.sepa.domain.entity.Societe;
import com.zina.sepa.domain.entity.User;
import com.zina.sepa.domain.enums.Role;
import com.zina.sepa.repository.SocieteRepository;
import com.zina.sepa.repository.UserRepository;
import com.zina.sepa.service.AuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AffectationServiceTest {

    @Mock AffectationRepository repository;
    @Mock SocieteRepository societeRepository;
    @Mock UserRepository userRepository;
    @Mock AuditService auditService;

    private AffectationService service() {
        return new AffectationService(repository, societeRepository, userRepository, auditService);
    }

    private User utilisateur(long id, String username, Role role, boolean actif) {
        User u = User.builder().id(id).username(username).nomComplet(username).role(role).actif(actif).build();
        when(userRepository.findById(id)).thenReturn(Optional.of(u));
        return u;
    }

    @Test
    void reaffecter_une_societe_cloture_l_ancienne_affectation_et_la_garde_dans_l_historique() {
        when(societeRepository.findByCode("SOC-001")).thenReturn(Optional.of(Societe.builder().code("SOC-001").nom("Zina").build()));
        utilisateur(2L, "expert2", Role.EXPERT_RH, true);
        AffectationSociete ancienne = AffectationSociete.builder().id(1L).societeCode("SOC-001").expertId(1L)
                .expertUsername("expert1").debutLe(LocalDateTime.now().minusDays(3)).build();
        when(repository.findBySocieteCodeAndFinLeIsNull("SOC-001")).thenReturn(Optional.of(ancienne));

        service().affecter("SOC-001", 2L, null);

        assertThat(ancienne.getFinLe()).isNotNull();
        ArgumentCaptor<AffectationSociete> sauvees = ArgumentCaptor.forClass(AffectationSociete.class);
        verify(repository, times(2)).save(sauvees.capture());
        AffectationSociete nouvelle = sauvees.getAllValues().get(1);
        assertThat(nouvelle.getExpertUsername()).isEqualTo("expert2");
        assertThat(nouvelle.getFinLe()).isNull();
    }

    @Test
    void seul_un_expert_rh_actif_peut_recevoir_une_societe() {
        when(societeRepository.findByCode("SOC-001")).thenReturn(Optional.of(Societe.builder().code("SOC-001").build()));
        utilisateur(5L, "manager1", Role.MANAGER, true);
        utilisateur(6L, "expert9", Role.EXPERT_RH, false);

        assertThatThrownBy(() -> service().affecter("SOC-001", 5L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service().affecter("SOC-001", 6L, null)).isInstanceOf(IllegalArgumentException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void retirer_sans_affectation_active_est_refuse() {
        when(repository.findBySocieteCodeAndFinLeIsNull("SOC-009")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().retirer("SOC-009", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("SOC-009");
    }

    @Test
    void societes_de_l_expert_listent_ses_affectations_actives() {
        when(repository.findByExpertUsernameAndFinLeIsNull("expert1")).thenReturn(List.of(
                AffectationSociete.builder().societeCode("SOC-001").build(),
                AffectationSociete.builder().societeCode("SOC-003").build()));

        assertThat(service().societesDe("expert1")).containsExactlyInAnyOrder("SOC-001", "SOC-003");
    }
}
