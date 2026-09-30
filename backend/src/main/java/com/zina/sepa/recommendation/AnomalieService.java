package com.zina.sepa.recommendation;

import com.zina.sepa.ai.AiAnomalyService;
import com.zina.sepa.ai.dto.AnalyseResult;
import com.zina.sepa.ai.dto.ResultatDto;
import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.recommendation.dto.AnomalieDto;
import com.zina.sepa.recommendation.dto.DetectionResume;
import com.zina.sepa.repository.TransactionRepository;
import com.zina.sepa.service.BusinessControlService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Matérialise les anomalies détectées (règles métier + IA) dans un registre persistant,
 * attribue une recommandation à la création et gère le suivi de statut.
 */
@Service
public class AnomalieService {

    private static final Logger log = LoggerFactory.getLogger(AnomalieService.class);

    private final TransactionRepository transactionRepository;
    private final BusinessControlService businessControlService;
    private final AiAnomalyService aiAnomalyService;
    private final RecommendationEngine recommendationEngine;
    private final AnomalieRepository anomalieRepository;

    public AnomalieService(TransactionRepository transactionRepository,
                           BusinessControlService businessControlService,
                           AiAnomalyService aiAnomalyService,
                           RecommendationEngine recommendationEngine,
                           AnomalieRepository anomalieRepository) {
        this.transactionRepository = transactionRepository;
        this.businessControlService = businessControlService;
        this.aiAnomalyService = aiAnomalyService;
        this.recommendationEngine = recommendationEngine;
        this.anomalieRepository = anomalieRepository;
    }

    /** (Re)lance la détection et réconcilie le registre d'anomalies. */
    public DetectionResume detecter() {
        List<Transaction> transactions = transactionRepository.findAll();
        List<Anomalie> courantes = new ArrayList<>();
        courantes.addAll(anomaliesReglesMetier(transactions));
        courantes.addAll(anomaliesIa(transactions));

        int nouvelles = reconcilier(courantes);
        return new DetectionResume(
                nouvelles,
                anomalieRepository.countByStatut(AnomalieStatut.OUVERTE),
                anomalieRepository.countByStatut(AnomalieStatut.CORRIGEE),
                anomalieRepository.countByStatut(AnomalieStatut.IGNOREE));
    }

    /** Liste les anomalies, filtrées par statut si fourni. */
    public List<AnomalieDto> lister(String statut) {
        List<Anomalie> anomalies = (statut == null || statut.isBlank())
                ? anomalieRepository.findAll()
                : anomalieRepository.findByStatut(AnomalieStatut.valueOf(statut.toUpperCase()));
        return anomalies.stream().map(this::toDto).collect(Collectors.toList());
    }

    /** Change le statut d'une anomalie (commentaire obligatoire pour IGNOREE). */
    public AnomalieDto changerStatut(Long id, String statut, String commentaire) {
        Anomalie a = anomalieRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Anomalie introuvable : " + id));
        AnomalieStatut cible;
        try {
            cible = AnomalieStatut.valueOf(statut == null ? "" : statut.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Statut invalide : " + statut);
        }
        if (cible == AnomalieStatut.IGNOREE && (commentaire == null || commentaire.isBlank())) {
            throw new IllegalArgumentException("Un commentaire est obligatoire pour ignorer une anomalie.");
        }
        a.setStatut(cible);
        a.setCommentaireCorrection(commentaire);
        a.setCorrigeePar(utilisateurCourant());
        a.setCorrigeeLe(LocalDateTime.now());
        return toDto(anomalieRepository.save(a));
    }

    // ------------------------------------------------------------------
    // Détection
    // ------------------------------------------------------------------

    private List<Anomalie> anomaliesReglesMetier(List<Transaction> transactions) {
        List<Anomalie> resultat = new ArrayList<>();

        for (Transaction t : transactions) {
            String periode = periode(t);

            if (t.getIban() == null || t.getIban().isBlank() || !businessControlService.ibanValide(t.getIban())) {
                Anomalie a = base(AnomalieType.IBAN_INVALIDE, t, periode);
                a.setDetails("IBAN invalide : " + (t.getIban() == null ? "manquant" : t.getIban()));
                resultat.add(a);
            }
            if (t.getBic() != null && !t.getBic().isBlank() && !businessControlService.bicValide(t.getBic())) {
                Anomalie a = base(AnomalieType.BIC_INVALIDE, t, periode);
                a.setBic(t.getBic());
                a.setDetails("BIC invalide : " + t.getBic());
                resultat.add(a);
            }
            if (t.getMontant() == null || t.getMontant().compareTo(BigDecimal.ZERO) <= 0) {
                Anomalie a = base(AnomalieType.MONTANT_INVALIDE, t, periode);
                a.setDetails("Montant négatif ou nul");
                resultat.add(a);
            }
        }

        // Doublons (même IBAN + montant).
        Map<String, List<Transaction>> parIbanMontant = transactions.stream()
                .filter(t -> t.getIban() != null && t.getMontant() != null)
                .collect(Collectors.groupingBy(t -> t.getIban() + "|" + t.getMontant().toPlainString(),
                        LinkedHashMap::new, Collectors.toList()));
        for (List<Transaction> groupe : parIbanMontant.values()) {
            if (groupe.size() > 1) {
                Transaction t = groupe.get(0);
                Anomalie a = base(AnomalieType.DOUBLON_PAIEMENT, t, periode(t));
                a.setSignature(AnomalieType.DOUBLON_PAIEMENT + "|" + t.getIban() + "|" + t.getMontant().toPlainString());
                a.setNb(groupe.size());
                a.setDetails(groupe.size() + " fiches identiques (IBAN + montant)");
                resultat.add(a);
            }
        }

        // Même IBAN pour plusieurs salariés distincts.
        Map<String, List<Transaction>> parIban = transactions.stream()
                .filter(t -> t.getIban() != null && !t.getIban().isBlank())
                .collect(Collectors.groupingBy(Transaction::getIban, LinkedHashMap::new, Collectors.toList()));
        for (Map.Entry<String, List<Transaction>> e : parIban.entrySet()) {
            List<String> matricules = e.getValue().stream()
                    .map(Transaction::getEmployeId).filter(m -> m != null).distinct().collect(Collectors.toList());
            if (matricules.size() > 1) {
                Transaction t = e.getValue().get(0);
                Anomalie a = base(AnomalieType.IBAN_PARTAGE, t, periode(t));
                a.setSignature(AnomalieType.IBAN_PARTAGE + "|" + e.getKey());
                a.setNb(matricules.size());
                a.setMatricules(String.join(", ", matricules));
                a.setDetails("IBAN partagé par " + matricules.size() + " salariés");
                resultat.add(a);
            }
        }
        return resultat;
    }

    private List<Anomalie> anomaliesIa(List<Transaction> transactions) {
        List<Anomalie> resultat = new ArrayList<>();
        AnalyseResult ai;
        try {
            ai = aiAnomalyService.analyser(transactions);
        } catch (Exception e) {
            log.warn("Analyse IA indisponible pour la détection d'anomalies : {}", e.getMessage());
            return resultat;
        }
        if (ai == null || !ai.disponible() || ai.resultats() == null) {
            return resultat;
        }
        Map<Long, Transaction> parId = transactions.stream()
                .filter(t -> t.getId() != null)
                .collect(Collectors.toMap(Transaction::getId, t -> t, (x, y) -> x));
        for (ResultatDto r : ai.resultats()) {
            if (!r.isAnomaly()) {
                continue;
            }
            Transaction t = parId.get(r.ficheId());
            if (t == null) {
                continue;
            }
            Anomalie a = base(AnomalieType.MONTANT_SUSPECT, t, periode(t));
            String raisons = r.raisons() == null ? "" : String.join(" ; ", r.raisons());
            a.setRaisons(raisons);
            a.setDetails(raisons);
            resultat.add(a);
        }
        return resultat;
    }

    private int reconcilier(List<Anomalie> courantes) {
        Map<String, Anomalie> existantes = new LinkedHashMap<>();
        for (Anomalie a : anomalieRepository.findAll()) {
            if (a.getSignature() != null) {
                existantes.put(a.getSignature(), a);
            }
        }

        int nouvelles = 0;
        java.util.Set<String> signaturesCourantes = new java.util.HashSet<>();
        for (Anomalie courante : courantes) {
            String sig = courante.getSignature();
            signaturesCourantes.add(sig);
            Anomalie existante = existantes.get(sig);
            if (existante == null) {
                courante.setStatut(AnomalieStatut.OUVERTE);
                // Unique appel de calcul de recommandation à la création de l'anomalie.
                courante.setRecommandation(recommander(courante));
                anomalieRepository.save(courante);
                nouvelles++;
            } else if (existante.getStatut() != AnomalieStatut.IGNOREE) {
                existante.setDetails(courante.getDetails());
                existante.setTransactionId(courante.getTransactionId());
                existante.setMontant(courante.getMontant());
                if (existante.getStatut() == AnomalieStatut.CORRIGEE) {
                    existante.setStatut(AnomalieStatut.OUVERTE);
                }
                if (existante.getRecommandation() == null) {
                    copierContexte(courante, existante);
                    existante.setRecommandation(recommander(existante));
                }
                anomalieRepository.save(existante);
            }
        }

        // Anomalies qui ne sont plus détectées : marquées CORRIGEE (IGNOREE conservée).
        for (Anomalie existante : existantes.values()) {
            if (!signaturesCourantes.contains(existante.getSignature())
                    && existante.getStatut() == AnomalieStatut.OUVERTE) {
                existante.setStatut(AnomalieStatut.CORRIGEE);
                anomalieRepository.save(existante);
            }
        }
        return nouvelles;
    }

    /** Appel du moteur protégé : ne bloque jamais la détection. */
    private String recommander(Anomalie a) {
        try {
            return recommendationEngine.recommander(a);
        } catch (Exception e) {
            log.warn("Moteur de recommandation en échec ({}) : recommandation générique appliquée.", e.getMessage());
            return "Vérifier les données de la fiche de paie concernée, corriger l'erreur, puis relancer le traitement.";
        }
    }

    private AnomalieDto toDto(Anomalie a) {
        String reco = a.getRecommandation();
        if (reco == null || reco.isBlank()) {
            reco = recommander(a); // calcul à la volée pour les anciennes lignes, sans les modifier
        }
        return new AnomalieDto(
                a.getId(),
                a.getType(),
                a.getMatricule(),
                IbanUtil.masquer(a.getIban()),
                a.getMontant(),
                a.getPeriode(),
                a.getDetails(),
                reco,
                a.getStatut() == null ? null : a.getStatut().name(),
                a.getCommentaireCorrection(),
                a.getCorrigeePar(),
                a.getCorrigeeLe() == null ? null : a.getCorrigeeLe().toString(),
                a.getTransactionId());
    }

    private Anomalie base(String type, Transaction t, String periode) {
        Anomalie a = Anomalie.builder()
                .type(type)
                .matricule(t.getEmployeId())
                .iban(t.getIban())
                .montant(t.getMontant())
                .periode(periode)
                .transactionId(t.getId())
                .build();
        a.setSignature(type + "|" + t.getId());
        return a;
    }

    private void copierContexte(Anomalie source, Anomalie cible) {
        cible.setBic(source.getBic());
        cible.setNb(source.getNb());
        cible.setMatricules(source.getMatricules());
        cible.setRaisons(source.getRaisons());
        cible.setElementXsd(source.getElementXsd());
        cible.setMessageXsd(source.getMessageXsd());
        cible.setNet(source.getNet());
        cible.setBrut(source.getBrut());
    }

    private String periode(Transaction t) {
        LocalDateTime d = t.getCreeLe() != null ? t.getCreeLe() : LocalDateTime.now();
        return String.format("%04d-%02d", d.getYear(), d.getMonthValue());
    }

    private String utilisateurCourant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        return auth.getName();
    }
}
