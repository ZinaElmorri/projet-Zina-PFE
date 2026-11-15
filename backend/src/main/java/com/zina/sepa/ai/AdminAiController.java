package com.zina.sepa.ai;

import com.zina.sepa.ai.dto.TrainResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import com.zina.sepa.security.Roles;
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
@PreAuthorize(Roles.CONSULTATION)
public class AdminAiController {

    private final AiSupervisionService supervision;
    private final QualiteModeleService qualite;

    public AdminAiController(AiSupervisionService supervision, QualiteModeleService qualite) {
        this.supervision = supervision;
        this.qualite = qualite;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return supervision.etat();
    }

    /** Qualité du modèle (état en service + résultats du dernier banc d'essai) : réservé à l'administrateur. */
    @GetMapping("/qualite")
    @PreAuthorize(Roles.ADMIN)
    public Map<String, Object> qualite() {
        return qualite.qualite();
    }

    @GetMapping("/metrics")
    public Map<String, Object> metrics() {
        return supervision.metriques();
    }

    /** Réentraîne le modèle sur les données synthétiques ou réelles (source=synthetique|reel). */
    @PostMapping("/train")
    @PreAuthorize(Roles.ADMIN)
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
