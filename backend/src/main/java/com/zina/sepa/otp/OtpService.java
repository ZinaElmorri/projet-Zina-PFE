package com.zina.sepa.otp;

import com.zina.sepa.domain.entity.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;

/**
 * Vérification de l'adresse email par code OTP à 6 chiffres (second facteur de connexion).
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpChallengeRepository repository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;
    private final boolean enabled;
    private final int expirationMinutes;
    private final int maxAttempts;
    private final int maxResends;
    private final int resendCooldownSeconds;
    private final String mailFrom;

    public OtpService(OtpChallengeRepository repository,
                      JavaMailSender mailSender,
                      PasswordEncoder passwordEncoder,
                      @Value("${app.otp.enabled:true}") boolean enabled,
                      @Value("${app.otp.expiration-minutes:5}") int expirationMinutes,
                      @Value("${app.otp.max-attempts:5}") int maxAttempts,
                      @Value("${app.otp.max-resends:3}") int maxResends,
                      @Value("${app.otp.resend-cooldown-seconds:30}") int resendCooldownSeconds,
                      @Value("${app.otp.mail-from:no-reply@sepa.local}") String mailFrom) {
        this.repository = repository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
        this.enabled = enabled;
        this.expirationMinutes = expirationMinutes;
        this.maxAttempts = maxAttempts;
        this.maxResends = maxResends;
        this.resendCooldownSeconds = resendCooldownSeconds;
        this.mailFrom = mailFrom;
    }

    public record OtpEnvoi(String challengeId, String emailMasque) {}

    public boolean isEnabled() {
        return enabled;
    }

    /** Crée un nouveau défi pour l'utilisateur (invalide les précédents) et envoie le code par email. */
    @Transactional
    public OtpEnvoi demarrer(User user) {
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            throw new IllegalStateException("Aucune adresse email n'est associée à ce compte");
        }
        repository.deleteByUsername(user.getUsername());

        String code = genererCode();
        LocalDateTime now = LocalDateTime.now();
        OtpChallenge challenge = OtpChallenge.builder()
                .challengeId(genererChallengeId())
                .username(user.getUsername())
                .email(user.getEmail())
                .codeHash(passwordEncoder.encode(code))
                .expireLe(now.plusMinutes(expirationMinutes))
                .dernierEnvoi(now)
                .build();
        repository.save(challenge);
        envoyerEmail(challenge.getEmail(), code);
        return new OtpEnvoi(challenge.getChallengeId(), masquerEmail(challenge.getEmail()));
    }

    /** Génère et envoie un nouveau code pour un défi existant. */
    @Transactional
    public OtpEnvoi renvoyer(String challengeId) {
        OtpChallenge challenge = repository.findByChallengeId(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Session de vérification invalide ou expirée"));
        LocalDateTime now = LocalDateTime.now();
        if (challenge.getRenvois() >= maxResends) {
            throw new IllegalArgumentException("Nombre maximal de renvois atteint, veuillez vous reconnecter");
        }
        if (challenge.getDernierEnvoi().plusSeconds(resendCooldownSeconds).isAfter(now)) {
            throw new IllegalArgumentException(
                    "Veuillez patienter " + resendCooldownSeconds + " secondes avant de redemander un code");
        }

        String code = genererCode();
        challenge.setCodeHash(passwordEncoder.encode(code));
        challenge.setExpireLe(now.plusMinutes(expirationMinutes));
        challenge.setDernierEnvoi(now);
        challenge.setRenvois(challenge.getRenvois() + 1);
        challenge.setTentatives(0);
        repository.save(challenge);
        envoyerEmail(challenge.getEmail(), code);
        return new OtpEnvoi(challenge.getChallengeId(), masquerEmail(challenge.getEmail()));
    }

    /**
     * Vérifie le code saisi. Retourne le nom d'utilisateur si le code est valide (le défi est alors consommé).
     * Les tentatives échouées sont persistées malgré l'exception levée.
     */
    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public String verifier(String challengeId, String code) {
        OtpChallenge challenge = repository.findByChallengeId(challengeId)
                .orElseThrow(() -> new IllegalArgumentException("Session de vérification invalide ou expirée"));

        if (challenge.getExpireLe().isBefore(LocalDateTime.now())) {
            repository.delete(challenge);
            throw new IllegalArgumentException("Code expiré, veuillez vous reconnecter");
        }
        if (challenge.getTentatives() >= maxAttempts) {
            repository.delete(challenge);
            throw new IllegalArgumentException("Nombre maximal de tentatives atteint, veuillez vous reconnecter");
        }
        if (code == null || !passwordEncoder.matches(code, challenge.getCodeHash())) {
            int tentatives = challenge.getTentatives() + 1;
            if (tentatives >= maxAttempts) {
                repository.delete(challenge);
                throw new IllegalArgumentException("Code invalide. Nombre maximal de tentatives atteint, veuillez vous reconnecter");
            }
            challenge.setTentatives(tentatives);
            repository.save(challenge);
            throw new IllegalArgumentException(
                    "Code invalide (" + (maxAttempts - tentatives) + " tentative(s) restante(s))");
        }

        repository.delete(challenge);
        return challenge.getUsername();
    }

    private void envoyerEmail(String destinataire, String code) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailFrom);
        message.setTo(destinataire);
        message.setSubject("Plateforme SEPA - Votre code de vérification");
        message.setText("""
                Bonjour,

                Votre code de vérification est : %s

                Ce code est valable %d minutes. Ne le communiquez à personne.
                Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.

                Plateforme SEPA
                """.formatted(code, expirationMinutes));
        try {
            mailSender.send(message);
        } catch (MailException e) {
            log.error("Échec d'envoi du code OTP à {} : {}", masquerEmail(destinataire), e.getMessage());
            throw new IllegalStateException("Impossible d'envoyer le code de vérification par email");
        }
    }

    private static String genererCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String genererChallengeId() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    static String masquerEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
