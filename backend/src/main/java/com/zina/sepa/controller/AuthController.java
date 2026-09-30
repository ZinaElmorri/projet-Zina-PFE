package com.zina.sepa.controller;

import com.zina.sepa.dto.AuthResponse;
import com.zina.sepa.dto.LoginRequest;
import com.zina.sepa.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authService.login(request, httpRequest));
    }

    /**
     * Authentification biométrique par reconnaissance faciale.
     * TODO : intégrer la comparaison d'encodage facial (déléguée au module IA ou à une lib dédiée).
     */
    @PostMapping("/face-login")
    public ResponseEntity<String> faceLogin() {
        return ResponseEntity.status(501).body("Reconnaissance faciale non encore implémentée");
    }
}
