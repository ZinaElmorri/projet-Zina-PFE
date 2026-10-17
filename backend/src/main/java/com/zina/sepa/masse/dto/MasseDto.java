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
            String erreurControle
    ) {}

    public record PaiementsResponse(
            List<PaiementMasse> paiements,
            int nombre,
            BigDecimal total
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
            String genereLe
    ) {}

    public record FichierDetail(
            FichierResume resume,
            String xml,
            List<ErreurXsd> erreurs,
            String nomDebiteur,
            String ibanDebiteur,
            String dateExecution
    ) {}
}
