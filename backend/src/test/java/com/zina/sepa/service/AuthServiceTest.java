package com.zina.sepa.service;

import com.zina.sepa.domain.entity.User;
import com.zina.sepa.domain.enums.Role;
import com.zina.sepa.dto.AuthResponse;
import com.zina.sepa.dto.FaceLoginRequest;
import com.zina.sepa.dto.LoginRequest;
import com.zina.sepa.dto.VerifyOtpRequest;
import com.zina.sepa.face.FaceService;
import com.zina.sepa.otp.OtpService;
import com.zina.sepa.repository.UserRepository;
import com.zina.sepa.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Règle d'authentification adaptative : visage → session directe ; mot de passe → OTP obligatoire. */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock AuthenticationManager authenticationManager;
    @Mock UserDetailsService userDetailsService;
    @Mock JwtService jwtService;
    @Mock UserRepository userRepository;
    @Mock AuditService auditService;
    @Mock OtpService otpService;
    @Mock FaceService faceService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock UserDetails userDetails;

    private final User alice = User.builder().id(1L).username("expert1").nomComplet("Expert 1")
            .email("expert1@sepa.local").role(Role.EXPERT_RH).actif(true).build();

    @BeforeEach
    void preparer() {
        lenient().when(userRepository.findByUsername("expert1")).thenReturn(Optional.of(alice));
        lenient().when(userDetailsService.loadUserByUsername("expert1")).thenReturn(userDetails);
        lenient().when(jwtService.generateToken(userDetails)).thenReturn("jwt-token");
    }

    private AuthService service() {
        return new AuthService(authenticationManager, userDetailsService, jwtService, userRepository,
                auditService, otpService, faceService, passwordEncoder);
    }

    @Test
    void visage_reconnu_ouvre_la_session_sans_otp() {
        when(faceService.authentifier("expert1", "img")).thenReturn(alice);

        AuthResponse reponse = service().faceLogin(new FaceLoginRequest("expert1", "img"), null);

        assertThat(reponse.otpRequis()).isFalse();
        assertThat(reponse.token()).isEqualTo("jwt-token");
        assertThat(reponse.role()).isEqualTo("EXPERT_RH");
        verifyNoInteractions(otpService);
    }

    @Test
    void mot_de_passe_correct_exige_un_otp_et_n_ouvre_pas_de_session() {
        when(otpService.demarrer(alice)).thenReturn(new OtpService.OtpEnvoi("chal-1", "e***@sepa.local"));

        AuthResponse reponse = service().login(new LoginRequest("expert1", "expert1123"), null);

        assertThat(reponse.otpRequis()).isTrue();
        assertThat(reponse.challengeId()).isEqualTo("chal-1");
        assertThat(reponse.token()).isNull();
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void l_otp_reste_obligatoire_meme_si_le_reglage_otp_enabled_est_desactive() {
        lenient().when(otpService.isEnabled()).thenReturn(false);
        when(otpService.demarrer(alice)).thenReturn(new OtpService.OtpEnvoi("chal-1", "e***@sepa.local"));

        AuthResponse reponse = service().login(new LoginRequest("expert1", "expert1123"), null);

        assertThat(reponse.otpRequis()).isTrue();
        assertThat(reponse.token()).isNull();
    }

    @Test
    void la_session_n_est_ouverte_qu_apres_un_otp_correct() {
        when(otpService.verifier("chal-1", "123456")).thenReturn("expert1");

        AuthResponse reponse = service().verifierOtp(new VerifyOtpRequest("chal-1", "123456"), null);

        assertThat(reponse.token()).isEqualTo("jwt-token");
        assertThat(reponse.otpRequis()).isFalse();
    }

    @Test
    void otp_incorrect_n_ouvre_pas_de_session() {
        when(otpService.verifier("chal-1", "000000")).thenThrow(new IllegalArgumentException("Code invalide"));

        assertThatThrownBy(() -> service().verifierOtp(new VerifyOtpRequest("chal-1", "000000"), null))
                .isInstanceOf(IllegalArgumentException.class);
        verify(jwtService, never()).generateToken(any());
    }

    @Test
    void mauvais_mot_de_passe_ne_declenche_ni_otp_ni_session() {
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("x"));

        assertThatThrownBy(() -> service().login(new LoginRequest("expert1", "faux"), null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Identifiants invalides");
        verifyNoInteractions(otpService, jwtService);
    }

    @Test
    void visage_refuse_ne_donne_pas_de_session_ni_d_otp() {
        when(faceService.authentifier("expert1", "img")).thenThrow(new IllegalArgumentException("Visage non reconnu"));

        assertThatThrownBy(() -> service().faceLogin(new FaceLoginRequest("expert1", "img"), null))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(otpService, jwtService);
    }

    @Test
    void module_facial_indisponible_refuse_la_connexion_et_invite_a_utiliser_le_mot_de_passe() {
        when(faceService.authentifier("expert1", "img"))
                .thenThrow(new IllegalStateException("Service de reconnaissance faciale indisponible"));

        assertThatThrownBy(() -> service().faceLogin(new FaceLoginRequest("expert1", "img"), null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("mot de passe");
        verifyNoInteractions(otpService, jwtService);
    }

    @Test
    void erreur_inattendue_du_facial_ne_laisse_jamais_passer_sans_facteur() {
        when(faceService.authentifier("expert1", "img")).thenThrow(new NullPointerException("réponse vide"));

        assertThatThrownBy(() -> service().faceLogin(new FaceLoginRequest("expert1", "img"), null))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(otpService, jwtService);
    }
}
