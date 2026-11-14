package com.zina.sepa.service;

import com.zina.sepa.domain.entity.User;
import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.dto.AuthResponse;
import com.zina.sepa.dto.FaceLoginRequest;
import com.zina.sepa.dto.LoginRequest;
import com.zina.sepa.dto.ResendOtpRequest;
import com.zina.sepa.dto.VerifyOtpRequest;
import com.zina.sepa.face.FaceService;
import com.zina.sepa.otp.OtpService;
import com.zina.sepa.repository.UserRepository;
import com.zina.sepa.security.JwtService;
import com.zina.sepa.security.MethodeConnexion;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final OtpService otpService;
    private final FaceService faceService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService,
                       JwtService jwtService,
                       UserRepository userRepository,
                       AuditService auditService,
                       OtpService otpService,
                       FaceService faceService,
                       PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.otpService = otpService;
        this.faceService = faceService;
        this.passwordEncoder = passwordEncoder;
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = AuditService.extractIp(httpRequest);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (DisabledException e) {
            // Statut révélé seulement si le mot de passe est correct (le contrôle "disabled" précède celui du mot de passe).
            var user = userRepository.findByUsername(request.username())
                    .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                    .orElse(null);
            auditService.log(AuditAction.LOGIN, "Échec de connexion (compte inactif)", "ECHEC", request.username(), ip);
            if (user == null) {
                throw new IllegalArgumentException("Identifiants invalides");
            }
            throw new IllegalArgumentException(user.isApprouve()
                    ? "Compte désactivé, contactez l'administrateur"
                    : "Compte en attente d'approbation par l'administrateur");
        } catch (Exception e) {
            auditService.log(AuditAction.LOGIN, "Échec de connexion", "ECHEC", request.username(), ip);
            throw new IllegalArgumentException("Identifiants invalides");
        }

        var user = userRepository.findByUsername(request.username()).orElseThrow();
        return finaliserConnexion(user, MethodeConnexion.MOT_DE_PASSE, ip, "Connexion réussie");
    }

    /**
     * Connexion par reconnaissance faciale : si le visage correspond, la session est ouverte sans OTP.
     * Toute autre issue (visage refusé, module indisponible, erreur inattendue) refuse la connexion sans
     * jamais émettre de token : le client doit alors se reconnecter avec mot de passe + OTP.
     */
    public AuthResponse faceLogin(FaceLoginRequest request, HttpServletRequest httpRequest) {
        String ip = AuditService.extractIp(httpRequest);
        User user;
        try {
            user = faceService.authentifier(request.username(), request.image());
        } catch (IllegalArgumentException e) {
            auditService.log(AuditAction.LOGIN, "Échec reconnaissance faciale : " + e.getMessage(), "ECHEC",
                    request.username(), ip);
            throw e;
        } catch (RuntimeException e) {
            auditService.log(AuditAction.LOGIN, "Reconnaissance faciale indisponible : " + e.getMessage(), "ECHEC",
                    request.username(), ip);
            throw new IllegalStateException(
                    "Reconnaissance faciale indisponible : connectez-vous avec votre mot de passe (un code de vérification vous sera demandé).");
        }
        return finaliserConnexion(user, MethodeConnexion.FACIALE, ip, "Connexion réussie (reconnaissance faciale)");
    }

    /**
     * Décision unique selon la méthode : faciale → session créée ; mot de passe → code OTP exigé.
     * L'OTP ne dépend volontairement pas du réglage {@code app.otp.enabled} : un mot de passe seul n'ouvre jamais de session.
     */
    private AuthResponse finaliserConnexion(User user, MethodeConnexion methode, String ip, String details) {
        if (!methode.exigeOtp()) {
            return emettreToken(user, ip, details);
        }

        var envoi = otpService.demarrer(user);
        auditService.log(AuditAction.LOGIN, "Code OTP envoyé à " + envoi.emailMasque(), "EN_ATTENTE",
                user.getUsername(), ip);
        return AuthResponse.defiOtp(user.getUsername(), envoi.challengeId(), envoi.emailMasque());
    }

    public AuthResponse verifierOtp(VerifyOtpRequest request, HttpServletRequest httpRequest) {
        String ip = AuditService.extractIp(httpRequest);
        String username;
        try {
            username = otpService.verifier(request.challengeId(), request.code());
        } catch (IllegalArgumentException e) {
            auditService.log(AuditAction.LOGIN, "Vérification OTP échouée : " + e.getMessage(), "ECHEC",
                    "anonyme", ip);
            throw e;
        }
        var user = userRepository.findByUsername(username).orElseThrow();
        return emettreToken(user, ip, "Connexion réussie (email vérifié par OTP)");
    }

    public AuthResponse renvoyerOtp(ResendOtpRequest request) {
        var envoi = otpService.renvoyer(request.challengeId());
        return AuthResponse.defiOtp(null, envoi.challengeId(), envoi.emailMasque());
    }

    private AuthResponse emettreToken(User user, String ip, String details) {
        var userDetails = userDetailsService.loadUserByUsername(user.getUsername());
        String token = jwtService.generateToken(userDetails);

        user.setDerniereConnexion(LocalDateTime.now());
        userRepository.save(user);

        auditService.log(AuditAction.LOGIN, details, "SUCCES", user.getUsername(), ip);
        return AuthResponse.authentifie(token, user.getUsername(), user.getRole().name());
    }
}
