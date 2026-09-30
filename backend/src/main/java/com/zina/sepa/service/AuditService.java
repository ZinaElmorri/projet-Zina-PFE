package com.zina.sepa.service;

import com.zina.sepa.domain.entity.AuditLog;
import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Service de traçabilité : enregistre toutes les actions dans le journal d'audit.
 */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    public void log(AuditAction action, String details, String resultat, HttpServletRequest request) {
        AuditLog entry = AuditLog.builder()
                .username(currentUsername())
                .action(action)
                .details(details)
                .resultat(resultat)
                .adresseIp(extractIp(request))
                .build();
        repository.save(entry);
    }

    public void log(AuditAction action, String details, String resultat, String username, String ip) {
        AuditLog entry = AuditLog.builder()
                .username(username)
                .action(action)
                .details(details)
                .resultat(resultat)
                .adresseIp(ip)
                .build();
        repository.save(entry);
    }

    public Page<AuditLog> findAll(Pageable pageable) {
        return repository.findAll(pageable);
    }

    private String currentUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "anonyme";
    }

    public static String extractIp(HttpServletRequest request) {
        if (request == null) return "inconnue";
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}
