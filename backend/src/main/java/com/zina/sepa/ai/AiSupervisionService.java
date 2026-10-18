package com.zina.sepa.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.zina.sepa.ai.dto.AiHealth;
import com.zina.sepa.ai.dto.TrainResponse;
import com.zina.sepa.recommendation.Anomalie;
import com.zina.sepa.recommendation.AnomalieRepository;
import com.zina.sepa.recommendation.AnomalieStatut;
import com.zina.sepa.recommendation.AnomalieType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Supervision du modèle IA : état, métriques d'évaluation, retours des utilisateurs
 * (anomalies confirmées / ignorées) et réentraînement.
 */
@Service
public class AiSupervisionService {

    public static final String SOURCE_SYNTHETIQUE = "synthetique";
    public static final String SOURCE_REEL = "reel";

    private static final Logger log = LoggerFactory.getLogger(AiSupervisionService.class);

    private final AiProperties properties;
    private final MontantSuspectClient client;
    private final AiAnomalyService aiAnomalyService;
    private final AnomalieRepository anomalieRepository;

    public AiSupervisionService(AiProperties properties,
                                MontantSuspectClient client,
                                AiAnomalyService aiAnomalyService,
                                AnomalieRepository anomalieRepository) {
        this.properties = properties;
        this.client = client;
        this.aiAnomalyService = aiAnomalyService;
        this.anomalieRepository = anomalieRepository;
    }

    public Map<String, Object> etat() {
        Map<String, Object> etat = new LinkedHashMap<>();
        etat.put("active", properties.isEnabled());
        etat.put("seuilScore", properties.getScoreThreshold());
        etat.put("historiqueMois", properties.getHistoryMonths());
        AiHealth health = null;
        if (properties.isEnabled()) {
            try {
                health = client.health();
            } catch (Exception e) {
                log.warn("Supervision IA : module injoignable ({})", e.getMessage());
            }
        }
        boolean disponible = health != null && "UP".equalsIgnoreCase(health.status());
        etat.put("disponible", disponible);
        etat.put("modelVersion", disponible ? health.modelVersion() : null);
        etat.put("source", disponible ? health.source() : null);
        etat.put("nbEntrainement", disponible ? health.nbEntrainement() : null);
        return etat;
    }

    /** Évaluation du modèle (module IA) et retours des utilisateurs (base de données). */
    public Map<String, Object> metriques() {
        Map<String, Object> resultat = new LinkedHashMap<>();
        String etat = "INDISPONIBLE";
        JsonNode evaluation = null;
        if (properties.isEnabled()) {
            try {
                ResponseEntity<JsonNode> reponse = client.metrics();
                if (reponse.getStatusCode().value() == 202) {
                    etat = "EN_COURS";
                } else {
                    etat = "DISPONIBLE";
                    evaluation = reponse.getBody();
                }
            } catch (Exception e) {
                log.warn("Métriques IA indisponibles : {}", e.getMessage());
            }
        }
        resultat.put("etat", etat);
        resultat.put("evaluation", evaluation);
        resultat.put("retours", retours());
        return resultat;
    }

    /**
     * Précision observée = anomalies IA confirmées par un utilisateur / (confirmées + ignorées).
     * Les clôtures automatiques (anomalie plus détectée) ne comptent pas comme confirmations.
     */
    public Map<String, Object> retours() {
        long confirmees = anomalieRepository.countByTypeAndStatutAndCorrigeeParIsNotNull(
                AnomalieType.MONTANT_SUSPECT, AnomalieStatut.CORRIGEE);
        long ignorees = anomalieRepository.countByTypeAndStatut(AnomalieType.MONTANT_SUSPECT, AnomalieStatut.IGNOREE);
        long ouvertes = anomalieRepository.countByTypeAndStatut(AnomalieType.MONTANT_SUSPECT, AnomalieStatut.OUVERTE);
        Map<String, Object> retours = new LinkedHashMap<>();
        retours.put("confirmees", confirmees);
        retours.put("ignorees", ignorees);
        retours.put("enAttente", ouvertes);
        retours.put("precisionObservee", confirmees + ignorees == 0 ? null
                : Math.round(confirmees * 10000.0 / (confirmees + ignorees)) / 10000.0);
        return retours;
    }

    public TrainResponse reentrainer(String source) {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("Le module IA est désactivé : réentraînement impossible.");
        }
        if (SOURCE_REEL.equalsIgnoreCase(source)) {
            Set<Long> confirmees = anomalieRepository
                    .findByTypeAndStatutAndCorrigeeParIsNotNull(AnomalieType.MONTANT_SUSPECT, AnomalieStatut.CORRIGEE)
                    .stream().map(Anomalie::getTransactionId).filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            return aiAnomalyService.reentrainerSurDonneesReelles(confirmees);
        }
        if (source == null || source.isBlank() || SOURCE_SYNTHETIQUE.equalsIgnoreCase(source)) {
            return aiAnomalyService.reentrainer();
        }
        throw new IllegalArgumentException("Source d'entraînement inconnue : " + source
                + " (valeurs possibles : synthetique, reel).");
    }
}
