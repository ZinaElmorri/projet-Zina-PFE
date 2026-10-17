package com.zina.sepa.face;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zina.sepa.domain.entity.User;
import com.zina.sepa.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FaceServiceTest {

    @Mock
    FaceClient faceClient;
    @Mock
    UserRepository userRepository;

    private FaceService service() {
        return new FaceService(faceClient, userRepository, new ObjectMapper());
    }

    private User user(String encoding, boolean actif) {
        return User.builder().username("rh").faceEncoding(encoding).actif(actif).build();
    }

    @Test
    void enregistrementStockeLEncodage() {
        User u = user(null, true);
        when(userRepository.findByUsername("rh")).thenReturn(Optional.of(u));
        when(faceClient.encode("img")).thenReturn(List.of(0.6, 0.8));

        service().enregistrer("rh", "img");

        assertThat(u.getFaceEncoding()).isEqualTo("[0.6,0.8]");
        verify(userRepository).save(u);
    }

    @Test
    void visageCorrespondantAuthentifie() {
        when(userRepository.findByUsername("rh")).thenReturn(Optional.of(user("[0.6,0.8]", true)));
        when(faceClient.verify("img", List.of(0.6, 0.8))).thenReturn(new FaceClient.VerifyResponse(true, 0.7, 0.363));

        assertThat(service().authentifier("rh", "img").getUsername()).isEqualTo("rh");
    }

    @Test
    void visageDifferentRefuse() {
        when(userRepository.findByUsername("rh")).thenReturn(Optional.of(user("[0.6,0.8]", true)));
        when(faceClient.verify(any(), any())).thenReturn(new FaceClient.VerifyResponse(false, 0.1, 0.363));

        assertThatThrownBy(() -> service().authentifier("rh", "img"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(FaceService.NON_RECONNU);
    }

    @Test
    void compteSansVisageOuInactifRefuseSansAppelIa() {
        when(userRepository.findByUsername("rh")).thenReturn(Optional.of(user(null, true)));
        assertThatThrownBy(() -> service().authentifier("rh", "img")).hasMessage(FaceService.NON_RECONNU);

        when(userRepository.findByUsername("rh")).thenReturn(Optional.of(user("[0.6,0.8]", false)));
        assertThatThrownBy(() -> service().authentifier("rh", "img")).hasMessage(FaceService.NON_RECONNU);

        verifyNoInteractions(faceClient);
    }
}
