package com.zina.sepa.ai;

import com.zina.sepa.ai.dto.AnalyseResult;
import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.repository.TransactionRepository;
import com.zina.sepa.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de détection IA des montants suspects.
 * À invoquer après les contrôles métier et avant la génération XML.
 */
@RestController
@RequestMapping("/api/ai")
public class AiDetectionController {

    private final AiAnomalyService aiAnomalyService;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public AiDetectionController(AiAnomalyService aiAnomalyService,
                                 TransactionRepository transactionRepository,
                                 AuditService auditService) {
        this.aiAnomalyService = aiAnomalyService;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    @PostMapping("/detect")
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','GESTIONNAIRE_RH','VALIDATEUR')")
    public AnalyseResult detect(HttpServletRequest request) {
        AnalyseResult resultat = aiAnomalyService.analyser(transactionRepository.findAll());
        long suspects = resultat.resultats() == null ? 0
                : resultat.resultats().stream().filter(r -> r.isAnomaly()).count();
        auditService.log(AuditAction.ANOMALIE_DETECTEE,
                resultat.disponible() ? "Analyse IA : " + suspects + " montant(s) suspect(s)" : "Analyse IA indisponible",
                resultat.disponible() ? "SUCCES" : "ECHEC", request);
        return resultat;
    }
}
