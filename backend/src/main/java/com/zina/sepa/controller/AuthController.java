package com.zina.sepa.controller;

import com.zina.sepa.dto.AuthResponse;
import com.zina.sepa.dto.FaceLoginRequest;
import com.zina.sepa.dto.LoginRequest;
import com.zina.sepa.dto.RegisterRequest;
import com.zina.sepa.dto.ResendOtpRequest;
import com.zina.sepa.dto.VerifyOtpRequest;
import com.zina.sepa.service.AuthService;
import com.zina.sepa.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    public AuthController(AuthService authService, UserService userService) {
        this.authService = authService;
        this.userService = userService;
    }

    /** Inscription publique avec photo du visage ; le compte reste inactif jusqu'à approbation par l'administrateur. */
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        var user = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "username", user.getUsername(),
                "message", "Compte créé. Il sera actif après approbation par l'administrateur."));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    /** Étape 2 de la connexion : vérification du code OTP reçu par email. */
    @PostMapping("/verify-otp")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody VerifyOtpRequest request,
                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.verifierOtp(request, httpRequest));
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<AuthResponse> resendOtp(@Valid @RequestBody ResendOtpRequest request) {
        return ResponseEntity.ok(authService.renvoyerOtp(request));
    }

    /** Authentification biométrique : comparaison du visage capturé avec la référence enregistrée (module IA). */
    @PostMapping("/face-login")
    public ResponseEntity<AuthResponse> faceLogin(@Valid @RequestBody FaceLoginRequest request,
                                                  HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.faceLogin(request, httpRequest));
    }
}
