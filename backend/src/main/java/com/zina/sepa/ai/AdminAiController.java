package com.zina.sepa.ai;

import com.zina.sepa.ai.dto.TrainResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import java.util.Map;

/**
 * Supervision du module IA : consultation pour les utilisateurs métier, réentraînement réservé
 * à l'administrateur système.
 */
@RestController
@RequestMapping("/api/admin/ai")
@PreAuthorize("hasAnyRole('ADMINISTRATEUR','GESTIONNAIRE_RH','VALIDATEUR')")
public class AdminAiController {

    private final AiSupervisionService supervision;

    public AdminAiController(AiSupervisionService supervision) {
        this.supervision = supervision;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return supervision.etat();
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return supervision.metriques();
    }

    /** Réentraîne le modèle sur les données synthétiques ou réelles (source=synthetique|reel). */
    @PostMapping("/train")
    @PreAuthorize("hasRole('ADMINISTRATEUR')")
    public ResponseEntity<?> train(@RequestParam(defaultValue = AiSupervisionService.SOURCE_SYNTHETIQUE) String source) {
        try {
            TrainResponse response = supervision.reentrainer(source);
            return ResponseEntity.ok(response);
        } catch (RestClientException e) {
            return ResponseEntity.status(503)
                    .body(Map.of("erreur", "Module IA indisponible : réentraînement impossible pour le moment."));
        }
    }
}
