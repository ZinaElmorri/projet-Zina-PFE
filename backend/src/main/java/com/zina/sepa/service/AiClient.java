package com.zina.sepa.service;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.enums.RiskLevel;
import com.zina.sepa.dto.AnomalyScore;
import com.zina.sepa.repository.TransactionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Client REST vers le module IA (FastAPI) pour la détection d'anomalies.
 */
@Service
public class AiClient {

    private final RestClient restClient;
    private final TransactionRepository transactionRepository;

    @Value("${app.ai-module.url}")
    private String aiModuleUrl;

    public AiClient(RestClient restClient, TransactionRepository transactionRepository) {
        this.restClient = restClient;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Envoie les transactions au module IA et met à jour leur score de risque.
     */
    @SuppressWarnings("unchecked")
    public List<AnomalyScore> analyser(List<Transaction> transactions) {
        List<Map<String, Object>> payload = transactions.stream()
                .map(t -> Map.<String, Object>of(
                        "transactionId", t.getId(),
                        "employeId", t.getEmployeId(),
                        "iban", t.getIban(),
                        "montant", t.getMontant()
                ))
                .toList();

        List<Map<String, Object>> reponse = restClient.post()
                .uri(aiModuleUrl + "/api/detect")
                .body(Map.of("transactions", payload))
                .retrieve()
                .body(List.class);

        if (reponse == null) return List.of();

        return reponse.stream().map(r -> {
            Long id = ((Number) r.get("transactionId")).longValue();
            double score = ((Number) r.get("scoreRisque")).doubleValue();
            String niveau = (String) r.get("niveauRisque");
            String explication = (String) r.getOrDefault("explication", "");

            transactionRepository.findById(id).ifPresent(t -> {
                t.setScoreRisque(score);
                t.setNiveauRisque(RiskLevel.valueOf(niveau));
                transactionRepository.save(t);
            });
            return new AnomalyScore(id, score, niveau, explication);
        }).toList();
    }
}
