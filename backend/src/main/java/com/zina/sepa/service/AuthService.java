package com.zina.sepa.service;

import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.dto.AuthResponse;
import com.zina.sepa.dto.LoginRequest;
import com.zina.sepa.repository.UserRepository;
import com.zina.sepa.security.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AuthService(AuthenticationManager authenticationManager,
                       UserDetailsService userDetailsService,
                       JwtService jwtService,
                       UserRepository userRepository,
                       AuditService auditService) {
        this.authenticationManager = authenticationManager;
        this.userDetailsService = userDetailsService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String ip = AuditService.extractIp(httpRequest);
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        } catch (Exception e) {
            auditService.log(AuditAction.LOGIN, "Échec de connexion", "ECHEC", request.username(), ip);
            throw new IllegalArgumentException("Identifiants invalides");
        }

        var userDetails = userDetailsService.loadUserByUsername(request.username());
        String token = jwtService.generateToken(userDetails);

        var user = userRepository.findByUsername(request.username()).orElseThrow();
        user.setDerniereConnexion(LocalDateTime.now());
        userRepository.save(user);

        auditService.log(AuditAction.LOGIN, "Connexion réussie", "SUCCES", user.getUsername(), ip);
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
}
