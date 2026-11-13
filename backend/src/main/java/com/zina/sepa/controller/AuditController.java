package com.zina.sepa.controller;

import com.zina.sepa.domain.entity.AuditLog;
import com.zina.sepa.service.AuditService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import com.zina.sepa.security.Roles;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit")
// Seul l'administrateur consulte le journal ; l'enregistrement des actions reste commun à tous les rôles.
@PreAuthorize(Roles.ADMIN)
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping
    public Page<AuditLog> list(Pageable pageable) {
        return auditService.findAll(pageable);
    }
}
