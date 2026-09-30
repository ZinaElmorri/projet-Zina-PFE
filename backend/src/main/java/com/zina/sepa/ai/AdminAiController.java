package com.zina.sepa.ai;

import com.zina.sepa.ai.dto.TrainResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Supervision et pilotage du module IA, réservés à l'administrateur système.
 */
@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasRole('ADMINISTRATEUR')")
public class AdminAiController {

    private final AiAnomalyService aiAnomalyService;

    public AdminAiController(AiAnomalyService aiAnomalyService) {
        this.aiAnomalyService = aiAnomalyService;
    }

    /** Indique si le module IA est disponible. */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("disponible", aiAnomalyService.disponible());
    }

    /** Déclenche un réentraînement du modèle. */
    @PostMapping("/train")
    public ResponseEntity<?> train() {
        try {
            TrainResponse response = aiAnomalyService.reentrainer();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(503)
                    .body(Map.of("erreur", "Module IA indisponible : " + e.getMessage()));
        }
    }
}
