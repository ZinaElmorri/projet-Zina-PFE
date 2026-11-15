package com.zina.sepa.masse.dto;

import java.math.BigDecimal;
import java.util.List;

/** Ensemble des DTO de la génération de virements de masse. */
public final class MasseDto {

    private MasseDto() {
    }

    // --- Analyse XSD ---

    public record ElementXsd(
            String nom,
            String chemin,
            boolean obligatoire,
            boolean repetable,
            String type,
            Integer maxLength,
            Integer minLength,
            String pattern,
            Integer fractionDigits,
            Integer totalDigits,
            List<String> valeursAutorisees,
            List<ElementXsd> enfants
    ) {}

    public record AnalyseXsd(
            String version,
            String namespace,
            int nbElements,
            int nbObligatoires,
            ElementXsd racine
    ) {}

    public record XsdUtilisable(
            Long id,
            String nom,
            int version,
            String versionMessage,
            boolean compatible,
            String raison
    ) {}

    // --- Filtres & paiements ---

    public record MatriculeDto(String matricule, String nom) {}

    public record FiltresResponse(
            List<String> societes,
            List<String> periodes,
            List<MatriculeDto> matricules
    ) {}

    public record PaiementMasse(
            Long id,
            String matricule,
            String nomBeneficiaire,
            String ibanMasque,
            String bic,
            BigDecimal montant,
            String devise,
            String statut,
            boolean enErreur,
            String erreurControle,
            boolean suspectIa,
            Double scoreIa,
            String raisonsIa
    ) {}

    public record PaiementsResponse(
            List<PaiementMasse> paiements,
            int nombre,
            BigDecimal total
    ) {}

    /** Résultat du contrôle IA sur la sélection (avertissement non bloquant). */
    public record AnalyseIaMasse(
            boolean disponible,
            String modelVersion,
            int nbAnalyses,
            int nbSuspects,
            PaiementsResponse paiements
    ) {}

    // --- Aperçu / génération / validation ---

    public record ErreurXsd(int ligne, int colonne, String message, String element) {}

    public record ApercuRequest(
            Long xsdSchemaId,
            String clientId,
            String periode,
            List<String> matricules,
            List<Long> transactionIds,
            String nomDebiteur,
            String ibanDebiteur,
            String bicDebiteur,
            String dateExecution,
            List<String> statuts,
            boolean exclureErreurs
    ) {}

    public record ApercuResponse(
            String xml,
            boolean valideXsd,
            List<ErreurXsd> erreurs,
            List<String> erreursControleMetier,
            List<String> avertissementsControleMetier,
            List<ErreurXsd> avertissements,
            List<ErreurXsd> erreursControleMetierXml,
            int nbTransactions,
            BigDecimal ctrlSum,
            String versionMessage,
            String msgId
    ) {}

    // --- Historique ---

    public record FichierResume(
            Long id,
            String nomFichier,
            String clientId,
            String periode,
            String matricules,
            int nbTransactions,
            BigDecimal ctrlSum,
            String xsdNom,
            String versionMessage,
            boolean valideXsd,
            int nbErreurs,
            String generePar,
            String genereLe,
            String statutFichier,
            Integer nbErreursTotal,
            Integer nbAvertissements,
            Integer nbSuspectsIa,
            String soumisPar,
            String decisionPar,
            String decisionLe,
            String motifRejet
    ) {}

    public record FichierDetail(
            FichierResume resume,
            String xml,
            List<ErreurXsd> erreurs,
            String nomDebiteur,
            String ibanDebiteur,
            String dateExecution
    ) {}

    // --- Résultat du contrôle (parcours en plusieurs pages) ---

    /** Message de contrôle métier rattaché à un salarié (matricule nul si non identifiable). */
    public record LigneControle(String matricule, String nom, String message) {}

    public record ResultatMetier(
            List<LigneControle> erreurs,
            List<LigneControle> avertissements,
            List<ErreurXsd> erreursXml,
            List<ErreurXsd> avertissementsXml
    ) {}

    public record LigneIa(
            Long transactionId,
            String matricule,
            String nom,
            BigDecimal montant,
            Double score,
            boolean suspect,
            List<String> raisons,
            String recommandation,
            /** FAIBLE, MOYEN ou ELEVE selon le score (null pour un fichier antérieur à ce champ). */
            String niveau,
            /** Type d'anomalie le plus probable (null si non fourni par le module IA). */
            String typeProbable
    ) {}

    /** {@code disponible} est faux si l'IA est désactivée, injoignable ou si le fichier est antérieur à l'analyse. */
    public record ResultatIa(
            boolean disponible,
            String modelVersion,
            int nbAnalyses,
            int nbSuspects,
            List<LigneIa> lignes
    ) {}

    public record ResultatFichier(
            FichierResume resume,
            String xml,
            String nomDebiteur,
            String ibanDebiteurMasque,
            String dateExecution,
            List<ErreurXsd> erreursXsd,
            List<ErreurXsd> avertissementsXsd,
            ResultatMetier metier,
            ResultatIa ia
    ) {}
}
