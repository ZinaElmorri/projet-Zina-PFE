package com.zina.sepa.ai;

import com.zina.sepa.ai.dto.AiHealth;
import com.zina.sepa.ai.dto.AnalyseResult;
import com.zina.sepa.ai.dto.MontantSuspectRequest;
import com.zina.sepa.ai.dto.MontantSuspectResponse;
import com.zina.sepa.ai.dto.PaiementDto;
import com.zina.sepa.ai.dto.ResultatDto;
import com.zina.sepa.ai.dto.TrainResponse;
import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.enums.RiskLevel;
import com.zina.sepa.repository.TransactionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestration de la détection IA des montants suspects.
 *
 * <p>Garanties de non-régression :</p>
 * <ul>
 *   <li>si {@code app.ai.enabled=false}, aucun appel n'est fait et le traitement continue ;</li>
 *   <li>toute erreur/timeout est capturée : on logge un avertissement et on continue
 *       sans anomalies IA (aucune exception ne remonte à l'appelant).</li>
 * </ul>
 */
@Service
public class AiAnomalyService {

    private static final Logger log = LoggerFactory.getLogger(AiAnomalyService.class);

    private static final String TYPE_MONTANT_SUSPECT = "MONTANT_SUSPECT";
    private static final String SOURCE_IA = "IA";
    static final int MIN_PAIEMENTS_ENTRAINEMENT = 100;
    private static final String RECOMMANDATION =
            "Vérifier primes/rappels dans la fiche de paie, confirmer ou corriger le montant.";

    private final AiProperties properties;
    private final MontantSuspectClient client;
    private final TransactionRepository transactionRepository;

    public AiAnomalyService(AiProperties properties,
                            MontantSuspectClient client,
                            TransactionRepository transactionRepository) {
        this.properties = properties;
        this.client = client;
        this.transactionRepository = transactionRepository;
    }

    /**
     * Analyse un lot de transactions et enregistre les anomalies IA détectées.
     * Ne lève jamais d'exception : renvoie un résultat « indisponible » en cas de souci.
     */
    public AnalyseResult analyser(List<Transaction> lot) {
        if (!properties.isEnabled()) {
            log.info("Module IA désactivé (app.ai.enabled=false) : traitement sans détection IA.");
            return new AnalyseResult(false, null, List.of());
        }
        if (lot == null || lot.isEmpty()) {
            return new AnalyseResult(true, null, List.of());
        }
        try {
            MontantSuspectResponse response = client.detect(construireRequete(lot));
            if (response == null || response.resultats() == null) {
                log.warn("Réponse IA vide : traitement poursuivi sans anomalies IA.");
                return new AnalyseResult(false, null, List.of());
            }
            appliquerResultats(lot, response);
            return new AnalyseResult(true, response.modelVersion(), response.resultats());
        } catch (Exception e) {
            log.warn("Détection IA indisponible ({}) : le traitement continue sans anomalies IA.",
                    e.getMessage());
            return new AnalyseResult(false, null, List.of());
        }
    }

    /** Indique si le module IA répond (supervision administrateur). */
    public boolean disponible() {
        if (!properties.isEnabled()) {
            return false;
        }
        try {
            AiHealth health = client.health();
            return health != null && "UP".equalsIgnoreCase(health.status());
        } catch (Exception e) {
            log.warn("Health-check IA en échec : {}", e.getMessage());
            return false;
        }
    }

    /** Réentraîne le modèle sur les données synthétiques de référence. */
    public TrainResponse reentrainer() {
        return client.train(null);
    }

    /**
     * Réentraîne le modèle sur les paiements réels, sans ceux dont l'anomalie a été confirmée
     * par un utilisateur : le modèle apprend ce qui est normal à partir de données vérifiées.
     */
    public TrainResponse reentrainerSurDonneesReelles(Set<Long> idsExclus) {
        List<Transaction> lot = transactionRepository.findAll().stream()
                .filter(t -> t.getId() != null && t.getMontant() != null && !idsExclus.contains(t.getId()))
                .collect(Collectors.toList());
        if (lot.size() < MIN_PAIEMENTS_ENTRAINEMENT) {
            throw new IllegalStateException("Réentraînement impossible : " + lot.size()
                    + " paiement(s) exploitable(s), il en faut au moins " + MIN_PAIEMENTS_ENTRAINEMENT + ".");
        }
        return client.train(construireRequete(lot));
    }

    private MontantSuspectRequest construireRequete(List<Transaction> lot) {
        Set<String> salaries = lot.stream().map(Transaction::getEmployeId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, List<Transaction>> parSalarie = salaries.isEmpty() ? Map.of()
                : transactionRepository.findByEmployeIdIn(salaries).stream()
                        .collect(Collectors.groupingBy(Transaction::getEmployeId));

        List<PaiementDto> paiements = new ArrayList<>();
        for (Transaction t : lot) {
            List<Transaction> anterieures = parSalarie.getOrDefault(t.getEmployeId(), List.of())
                    .stream()
                    .filter(a -> a.getId() != null && !a.getId().equals(t.getId()))
                    .sorted(Comparator.comparing(
                            Transaction::getCreeLe,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .collect(Collectors.toList());

            List<BigDecimal> historique = anterieures.stream()
                    .limit(properties.getHistoryMonths())
                    .map(Transaction::getMontant)
                    .filter(m -> m != null)
                    .collect(Collectors.toList());
            // Le modèle attend l'historique du plus ancien au plus récent.
            java.util.Collections.reverse(historique);

            boolean ibanChange = !anterieures.isEmpty()
                    && anterieures.get(0).getIban() != null
                    && !anterieures.get(0).getIban().equalsIgnoreCase(t.getIban());

            paiements.add(new PaiementDto(
                    t.getId(),
                    t.getEmployeId(),
                    null,
                    t.getMontant(),
                    t.getIban(),
                    ibanChange,
                    historique));
        }
        return new MontantSuspectRequest(periodeCourante(), paiements);
    }

    private void appliquerResultats(List<Transaction> lot, MontantSuspectResponse response) {
        Map<Long, ResultatDto> parFiche = response.resultats().stream()
                .filter(r -> r.ficheId() != null)
                .collect(Collectors.toMap(ResultatDto::ficheId, r -> r, (a, b) -> a));

        for (Transaction t : lot) {
            ResultatDto r = parFiche.get(t.getId());
            if (r != null && r.isAnomaly()) {
                t.setScoreRisque(r.score());
                t.setNiveauRisque(niveau(r.score()));
                t.setTypeAnomalie(TYPE_MONTANT_SUSPECT);
                t.setSourceAnomalie(SOURCE_IA);
                t.setModelVersion(response.modelVersion());
                t.setRaisonsAnomalie(r.raisons() == null ? "" : String.join(" ; ", r.raisons()));
                t.setRecommandation(RECOMMANDATION);
                transactionRepository.save(t);
            } else if (SOURCE_IA.equals(t.getSourceAnomalie())) {
                // Évite les doublons si le traitement est relancé : purge l'ancienne anomalie IA.
                t.setTypeAnomalie(null);
                t.setSourceAnomalie(null);
                t.setModelVersion(null);
                t.setRaisonsAnomalie(null);
                t.setRecommandation(null);
                transactionRepository.save(t);
            }
        }
    }

    private RiskLevel niveau(double score) {
        if (score >= 0.85) {
            return RiskLevel.CRITIQUE;
        }
        if (score >= 0.6) {
            return RiskLevel.ELEVE;
        }
        if (score >= 0.35) {
            return RiskLevel.MOYEN;
        }
        return RiskLevel.FAIBLE;
    }

    private String periodeCourante() {
        LocalDateTime now = LocalDateTime.now();
        return String.format("%04d-%02d", now.getYear(), now.getMonthValue());
    }
}
