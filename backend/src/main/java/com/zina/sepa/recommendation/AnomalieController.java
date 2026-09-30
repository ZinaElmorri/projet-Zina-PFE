package com.zina.sepa.recommendation;

import com.zina.sepa.recommendation.dto.AnomalieDto;
import com.zina.sepa.recommendation.dto.ChangementStatutRequest;
import com.zina.sepa.recommendation.dto.DetectionResume;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Suivi des anomalies : liste (filtrable), (re)détection et changement de statut.
 */
@RestController
@RequestMapping("/api/anomalies")
public class AnomalieController {

    private final AnomalieService anomalieService;

    public AnomalieController(AnomalieService anomalieService) {
        this.anomalieService = anomalieService;
    }

    /** Liste des anomalies ; sans paramètre, comportement inchangé (toutes). */
    @GetMapping
    public List<AnomalieDto> lister(@RequestParam(required = false) String statut) {
        return anomalieService.lister(statut);
    }

    /** Relance la détection et réconcilie le registre. */
    @PostMapping("/detecter")
    public DetectionResume detecter() {
        return anomalieService.detecter();
    }

    /** Change le statut d'une anomalie (commentaire obligatoire pour IGNOREE). */
    @PatchMapping("/{id}/statut")
    public AnomalieDto changerStatut(@PathVariable Long id,
                                     @RequestBody ChangementStatutRequest request) {
        return anomalieService.changerStatut(id, request.statut(), request.commentaire());
    }
}
