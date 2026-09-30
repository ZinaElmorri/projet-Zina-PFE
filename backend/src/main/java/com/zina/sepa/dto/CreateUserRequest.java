package com.zina.sepa.dto;

import com.zina.sepa.domain.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateUserRequest(
        @NotBlank String username,
        @NotBlank String password,
        @NotBlank String nomComplet,
        @Email String email,
        @NotNull Role role
) {}
