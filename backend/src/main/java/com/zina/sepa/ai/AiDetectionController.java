package com.zina.sepa.ai;

import com.zina.sepa.ai.dto.AnalyseResult;
import com.zina.sepa.repository.TransactionRepository;
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

    public AiDetectionController(AiAnomalyService aiAnomalyService,
                                 TransactionRepository transactionRepository) {
        this.aiAnomalyService = aiAnomalyService;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping("/detect")
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','GESTIONNAIRE_RH','VALIDATEUR')")
    public AnalyseResult detect() {
        return aiAnomalyService.analyser(transactionRepository.findAll());
    }
}
