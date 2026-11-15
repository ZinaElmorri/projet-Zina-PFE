package com.zina.sepa.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Qualité du modèle IA pour l'administrateur : état du modèle en service et résultats du dernier banc d'essai
 * ({@code resultats.json} produit par {@code ai-module/benchmark}). Lecture seule, uniquement des agrégats :
 * aucune donnée de salarié.
 */
@Service
public class QualiteModeleService {

    private static final Logger log = LoggerFactory.getLogger(QualiteModeleService.class);
    private static final Pattern DATE_VERSION = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    private final AiProperties properties;
    private final AiSupervisionService supervision;
    private final ObjectMapper mapper;

    public QualiteModeleService(AiProperties properties, AiSupervisionService supervision, ObjectMapper mapper) {
        this.properties = properties;
        this.supervision = supervision;
        this.mapper = mapper;
    }

    public Map<String, Object> qualite() {
        Map<String, Object> modele = new LinkedHashMap<>(supervision.etat());
        modele.put("dateEntrainement", dateEntrainement((String) modele.get("modelVersion")));

        Map<String, Object> reponse = new LinkedHashMap<>();
        reponse.put("modele", modele);
        JsonNode banc = lireBanc();
        reponse.put("bancDisponible", banc != null);
        reponse.put("banc", banc);
        reponse.put("dateBanc", banc == null ? null : dateFichier());
        return reponse;
    }

    /** La version du modèle porte sa date d'entraînement (ex. iforest-2026-09-25) ; null si elle n'y figure pas. */
    static String dateEntrainement(String version) {
        if (version == null) {
            return null;
        }
        Matcher m = DATE_VERSION.matcher(version);
        return m.find() ? m.group(1) : null;
    }

    /** Sous-ensemble du fichier de résultats utile à l'écran ; null si le fichier est absent ou illisible. */
    JsonNode lireBanc() {
        Path chemin = Path.of(properties.getBenchmarkFile());
        if (!Files.isReadable(chemin)) {
            log.info("Résultats du banc d'essai IA introuvables : {}", chemin.toAbsolutePath());
            return null;
        }
        try {
            JsonNode racine = mapper.readTree(Files.readAllBytes(chemin));
            ObjectNode banc = mapper.createObjectNode();
            copier(racine, banc, "evaluation");
            copier(racine, banc, "candidat");
            JsonNode donnees = racine.get("jeuDeDonnees");
            if (donnees != null) {
                ObjectNode resume = mapper.createObjectNode();
                for (String champ : new String[]{"nbSocietes", "nbSalaries", "nbMois", "periode", "nbFiches",
                        "nbAnomalies", "tauxAnomalies", "partitions", "anomaliesParType"}) {
                    copier(donnees, resume, champ);
                }
                banc.set("jeuDeDonnees", resume);
            }
            JsonNode approches = racine.path("comparaison").get("approches");
            if (approches != null) {
                ObjectNode reduit = mapper.createObjectNode();
                approches.fields().forEachRemaining(e -> {
                    ObjectNode a = mapper.createObjectNode();
                    a.set("nom", e.getValue().get("nom"));
                    a.set("metriques", e.getValue().get("metriques"));
                    reduit.set(e.getKey(), a);
                });
                banc.set("comparaison", reduit);
            }
            return banc;
        } catch (IOException e) {
            log.warn("Résultats du banc d'essai IA illisibles ({})", e.getMessage());
            return null;
        }
    }

    private static void copier(JsonNode source, ObjectNode cible, String champ) {
        JsonNode valeur = source.get(champ);
        if (valeur != null) {
            cible.set(champ, valeur);
        }
    }

    private String dateFichier() {
        try {
            return Instant.ofEpochMilli(Files.getLastModifiedTime(Path.of(properties.getBenchmarkFile())).toMillis()).toString();
        } catch (IOException e) {
            return null;
        }
    }
}
