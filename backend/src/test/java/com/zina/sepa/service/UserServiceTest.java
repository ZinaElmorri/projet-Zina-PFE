package com.zina.sepa.service;

import com.zina.sepa.domain.entity.User;
import com.zina.sepa.domain.enums.Role;
import com.zina.sepa.dto.RegisterRequest;
import com.zina.sepa.face.FaceService;
import com.zina.sepa.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    UserRepository userRepository;
    @Mock
    FaceService faceService;

    private UserService service() {
        return new UserService(userRepository, new BCryptPasswordEncoder(4), faceService);
    }

    private RegisterRequest request() {
        return new RegisterRequest("zina", "motdepasse1", "Zina M", "zina@sepa.local", Role.VALIDATEUR, "img");
    }

    @Test
    void inscriptionCreeUnCompteInactifAvecVisage() {
        when(faceService.encoder("img")).thenReturn("[0.6,0.8]");
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));

        User u = service().register(request());

        assertThat(u.isActif()).isFalse();
        assertThat(u.isApprouve()).isFalse();
        assertThat(u.getRole()).isEqualTo(Role.VALIDATEUR);
        assertThat(u.getFaceEncoding()).isEqualTo("[0.6,0.8]");
        assertThat(u.getPassword()).isNotEqualTo("motdepasse1");
    }

    @Test
    void inscriptionRefuseEmailDejaUtilise() {
        when(userRepository.existsByEmailIgnoreCase("zina@sepa.local")).thenReturn(true);

        assertThatThrownBy(() -> service().register(request())).hasMessageContaining("email");
        verify(userRepository, never()).save(any());
    }

    @Test
    void approbationActiveLeCompte() {
        User u = User.builder().id(5L).username("zina").actif(false).approuve(false).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(u));
        when(userRepository.save(u)).thenReturn(u);

        service().approuver(5L);

        assertThat(u.isActif()).isTrue();
        assertThat(u.isApprouve()).isTrue();
    }

    @Test
    void rejetSupprimeUniquementUneDemandeEnAttente() {
        User attente = User.builder().id(5L).approuve(false).build();
        User approuve = User.builder().id(6L).approuve(true).build();
        when(userRepository.findById(5L)).thenReturn(Optional.of(attente));
        when(userRepository.findById(6L)).thenReturn(Optional.of(approuve));

        service().rejeter(5L);
        verify(userRepository).delete(attente);

        assertThatThrownBy(() -> service().rejeter(6L)).isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).delete(approuve);
    }
}
