package com.zina.sepa.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.zina.sepa.ai.dto.AiHealth;
import com.zina.sepa.ai.dto.MontantSuspectRequest;
import com.zina.sepa.ai.dto.MontantSuspectResponse;
import com.zina.sepa.ai.dto.TrainResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client HTTP vers le module IA « montants suspects ».
 * Ne gère pas les erreurs : la dégradation gracieuse est assurée par {@link AiAnomalyService}.
 */
@Component
public class MontantSuspectClient {

    private final RestClient aiRestClient;

    public MontantSuspectClient(@Qualifier("aiRestClient") RestClient aiRestClient) {
        this.aiRestClient = aiRestClient;
    }

    public MontantSuspectResponse detect(MontantSuspectRequest request) {
        return aiRestClient.post()
                .uri("/anomalies/detect")
                .body(request)
                .retrieve()
                .body(MontantSuspectResponse.class);
    }

    public TrainResponse train(MontantSuspectRequest request) {
        return aiRestClient.post()
                .uri("/model/train")
                .body(request != null ? request : new MontantSuspectRequest(null, java.util.List.of()))
                .retrieve()
                .body(TrainResponse.class);
    }

    /** Rapport d'évaluation du modèle (statut 202 tant que le calcul est en cours). */
    public ResponseEntity<JsonNode> metrics() {
        return aiRestClient.get()
                .uri("/model/metrics")
                .retrieve()
                .toEntity(JsonNode.class);
    }

    public AiHealth health() {
        return aiRestClient.get()
                .uri("/model/health")
                .retrieve()
                .body(AiHealth.class);
    }
}
