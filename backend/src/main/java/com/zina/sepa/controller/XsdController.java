package com.zina.sepa.controller;

import com.zina.sepa.domain.entity.XsdSchema;
import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.service.AuditService;
import com.zina.sepa.service.XsdService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/xsd")
public class XsdController {

    private final XsdService xsdService;
    private final AuditService auditService;

    public XsdController(XsdService xsdService, AuditService auditService) {
        this.xsdService = xsdService;
        this.auditService = auditService;
    }

    @GetMapping
    public List<XsdSchema> list() {
        return xsdService.findAll();
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','GESTIONNAIRE_RH')")
    public ResponseEntity<XsdSchema> importer(@RequestParam("file") MultipartFile file,
                                              HttpServletRequest request) throws IOException {
        String username = request.getUserPrincipal() != null
                ? request.getUserPrincipal().getName() : "anonyme";
        XsdSchema schema = xsdService.importer(file, username);
        auditService.log(AuditAction.XSD_IMPORTE,
                "Import XSD " + schema.getNom() + " v" + schema.getVersion(), "SUCCES", request);
        return ResponseEntity.ok(schema);
    }

    @GetMapping("/{id}/elements")
    public List<String> analyser(@PathVariable Long id) {
        return xsdService.analyserElements(id);
    }
}
