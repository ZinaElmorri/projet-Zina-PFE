package com.zina.sepa.otp;

import com.zina.sepa.domain.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock
    OtpChallengeRepository repository;
    @Mock
    JavaMailSender mailSender;

    private OtpService service() {
        return new OtpService(repository, mailSender, new BCryptPasswordEncoder(4),
                true, 5, 3, 3, 30, "no-reply@sepa.local");
    }

    private User user() {
        return User.builder().username("rh").email("rh@sepa.local").build();
    }

    private String codeEnvoye() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertThat(captor.getValue().getTo()).containsExactly("rh@sepa.local");
        Matcher m = Pattern.compile("\\b(\\d{6})\\b").matcher(captor.getValue().getText());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private OtpChallenge demarrerEtCapturer(OtpService service) {
        ArgumentCaptor<OtpChallenge> captor = ArgumentCaptor.forClass(OtpChallenge.class);
        var envoi = service.demarrer(user());
        verify(repository).save(captor.capture());
        assertThat(envoi.emailMasque()).isEqualTo("r***@sepa.local");
        assertThat(envoi.challengeId()).isEqualTo(captor.getValue().getChallengeId());
        return captor.getValue();
    }

    @Test
    void codeValideConsommeLeDefi() {
        OtpService service = service();
        OtpChallenge challenge = demarrerEtCapturer(service);
        String code = codeEnvoye();
        assertThat(challenge.getCodeHash()).isNotEqualTo(code);
        when(repository.findByChallengeId(challenge.getChallengeId())).thenReturn(Optional.of(challenge));

        assertThat(service.verifier(challenge.getChallengeId(), code)).isEqualTo("rh");
        verify(repository).delete(challenge);
    }

    @Test
    void codeInvalideIncrementeLesTentativesPuisBloque() {
        OtpService service = service();
        OtpChallenge challenge = demarrerEtCapturer(service);
        when(repository.findByChallengeId(challenge.getChallengeId())).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.verifier(challenge.getChallengeId(), "000000x"))
                .hasMessageContaining("2 tentative(s) restante(s)");
        assertThatThrownBy(() -> service.verifier(challenge.getChallengeId(), "000000x"))
                .hasMessageContaining("1 tentative(s) restante(s)");
        assertThatThrownBy(() -> service.verifier(challenge.getChallengeId(), "000000x"))
                .hasMessageContaining("Nombre maximal de tentatives");
        verify(repository).delete(challenge);
    }

    @Test
    void codeExpireEstRefuse() {
        OtpService service = service();
        OtpChallenge challenge = demarrerEtCapturer(service);
        String code = codeEnvoye();
        challenge.setExpireLe(LocalDateTime.now().minusSeconds(1));
        when(repository.findByChallengeId(challenge.getChallengeId())).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.verifier(challenge.getChallengeId(), code))
                .hasMessageContaining("expiré");
    }

    @Test
    void renvoiTropRapideEstRefuse() {
        OtpService service = service();
        OtpChallenge challenge = demarrerEtCapturer(service);
        when(repository.findByChallengeId(challenge.getChallengeId())).thenReturn(Optional.of(challenge));

        assertThatThrownBy(() -> service.renvoyer(challenge.getChallengeId()))
                .hasMessageContaining("patienter");
    }

    @Test
    void echecSmtpRemonteUneErreur() {
        doThrow(new MailSendException("SMTP indisponible")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> service().demarrer(user()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Impossible d'envoyer");
    }

    @Test
    void compteSansEmailEstRefuse() {
        User sansEmail = User.builder().username("x").build();
        assertThatThrownBy(() -> service().demarrer(sansEmail))
                .isInstanceOf(IllegalStateException.class);
    }
}
