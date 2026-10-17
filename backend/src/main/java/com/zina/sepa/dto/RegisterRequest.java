package com.zina.sepa.dto;

import com.zina.sepa.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 50)
        @Pattern(regexp = "[A-Za-z0-9._-]+", message = "Lettres, chiffres, '.', '_' et '-' uniquement")
        String username,
        @NotBlank @Size(min = 8, max = 100, message = "Le mot de passe doit contenir au moins 8 caractères")
        String password,
        @NotBlank @Size(max = 100) String nomComplet,
        @NotBlank @Email String email,
        @NotNull Role role,
        @NotBlank(message = "Une photo du visage est obligatoire") @Size(max = 6_000_000) String image
) {}
