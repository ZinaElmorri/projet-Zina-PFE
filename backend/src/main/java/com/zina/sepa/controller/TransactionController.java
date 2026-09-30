package com.zina.sepa.controller;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.dto.AnomalyScore;
import com.zina.sepa.dto.TransactionRequest;
import com.zina.sepa.dto.ValidationResult;
import com.zina.sepa.repository.TransactionRepository;
import com.zina.sepa.service.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionRepository repository;
    private final BusinessControlService businessControlService;
    private final XmlGenerationService xmlGenerationService;
    private final AiClient aiClient;
    private final AuditService auditService;

    public TransactionController(TransactionRepository repository,
                                 BusinessControlService businessControlService,
                                 XmlGenerationService xmlGenerationService,
                                 AiClient aiClient,
                                 AuditService auditService) {
        this.repository = repository;
        this.businessControlService = businessControlService;
        this.xmlGenerationService = xmlGenerationService;
        this.aiClient = aiClient;
        this.auditService = auditService;
    }

    @GetMapping
    public List<Transaction> list() {
        return repository.findAll();
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','GESTIONNAIRE_RH')")
    public ResponseEntity<Transaction> create(@Valid @RequestBody TransactionRequest req,
                                              HttpServletRequest request) {
        Transaction t = Transaction.builder()
                .employeId(req.employeId())
                .nomBeneficiaire(req.nomBeneficiaire())
                .iban(req.iban())
                .bic(req.bic())
                .montant(req.montant())
                .devise(req.devise() != null ? req.devise() : "EUR")
                .libelle(req.libelle())
                .statut(TransactionStatus.BROUILLON)
                .build();
        Transaction saved = repository.save(t);
        auditService.log(AuditAction.TRANSACTION_CREEE,
                "Transaction #" + saved.getId(), "SUCCES", request);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}/approuver")
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','VALIDATEUR')")
    public ResponseEntity<Transaction> approuver(@PathVariable Long id, HttpServletRequest request) {
        Transaction t = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Transaction introuvable"));
        t.setStatut(TransactionStatus.APPROUVEE);
        repository.save(t);
        auditService.log(AuditAction.TRANSACTION_APPROUVEE,
                "Transaction #" + id, "SUCCES", request);
        return ResponseEntity.ok(t);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','GESTIONNAIRE_RH')")
    public ResponseEntity<Void> supprimer(@PathVariable Long id, HttpServletRequest request) {
        repository.deleteById(id);
        auditService.log(AuditAction.TRANSACTION_SUPPRIMEE,
                "Transaction #" + id, "SUCCES", request);
        return ResponseEntity.noContent().build();
    }

    /** Contrôles métier RH sur toutes les transactions en brouillon. */
    @PostMapping("/controler")
    public ValidationResult controler(HttpServletRequest request) {
        var result = businessControlService.controle(
                repository.findByStatut(TransactionStatus.BROUILLON));
        auditService.log(AuditAction.VALIDATION_EFFECTUEE,
                "Contrôles métier : " + result.erreurs().size() + " erreur(s)",
                result.valide() ? "SUCCES" : "ECHEC", request);
        return result;
    }

    /** Détection d'anomalies via le module IA. */
    @PostMapping("/analyser")
    public List<AnomalyScore> analyser(HttpServletRequest request) {
        var scores = aiClient.analyser(repository.findAll());
        auditService.log(AuditAction.ANOMALIE_DETECTEE,
                scores.size() + " transaction(s) analysée(s)", "SUCCES", request);
        return scores;
    }

    /** Génère le fichier SEPA pain.001 pour les transactions approuvées. */
    @PostMapping(value = "/generer", produces = MediaType.APPLICATION_XML_VALUE)
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','GESTIONNAIRE_RH')")
    public ResponseEntity<String> generer(@RequestParam String nomDebiteur,
                                          @RequestParam String ibanDebiteur,
                                          HttpServletRequest request) {
        var transactions = repository.findByStatut(TransactionStatus.APPROUVEE);
        String xml = xmlGenerationService.genererPain001(transactions, nomDebiteur, ibanDebiteur);
        auditService.log(AuditAction.XML_GENERE,
                transactions.size() + " transaction(s)", "SUCCES", request);
        return ResponseEntity.ok(xml);
    }
}
