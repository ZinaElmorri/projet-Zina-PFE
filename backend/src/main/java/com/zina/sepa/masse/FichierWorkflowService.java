package com.zina.sepa.masse;

import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.masse.dto.MasseDto.FichierResume;
import com.zina.sepa.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Circuit de validation d'un fichier : BROUILLON → SOUMIS (Expert RH) → VALIDE ou REJETE (Manager).
 * Principe des quatre yeux : celui qui a généré ou soumis un fichier ne peut pas le valider ni le rejeter.
 */
@Service
public class FichierWorkflowService {

    private final FichierSepaGenereRepository repository;
    private final MassPaymentService massPaymentService;
    private final AccesSocietes acces;
    private final AuditService auditService;

    public FichierWorkflowService(FichierSepaGenereRepository repository, MassPaymentService massPaymentService,
                                  AccesSocietes acces, AuditService auditService) {
        this.repository = repository;
        this.massPaymentService = massPaymentService;
        this.acces = acces;
        this.auditService = auditService;
    }

    public FichierResume soumettre(Long id, HttpServletRequest http) {
        FichierSepaGenere f = charger(id);
        acces.verifier(f.getClientId());
        if (!f.isValideXsd()) {
            throw new IllegalStateException("Le XML n'est pas conforme au XSD : il ne peut pas être soumis. Corrigez les erreurs puis relancez le contrôle.");
        }
        int erreursMetier = massPaymentService.nbErreursMetier(f);
        if (erreursMetier > 0) {
            throw new IllegalStateException("Le fichier contient " + erreursMetier + " erreur(s) de contrôle métier : il ne peut pas être soumis. "
                    + "Corrigez les paiements concernés ou excluez-les de la sélection, puis relancez le contrôle.");
        }
        exigerStatut(f, MassPaymentService.STATUT_BROUILLON, "soumis");
        f.setStatutFichier(MassPaymentService.STATUT_SOUMIS);
        f.setSoumisPar(utilisateur());
        f.setSoumisLe(LocalDateTime.now());
        repository.save(f);
        auditService.log(AuditAction.FICHIER_SOUMIS, "Fichier #" + id + " (" + f.getNomFichier() + ") soumis à validation",
                "SUCCES", http);
        return massPaymentService.resume(f);
    }

    public FichierResume valider(Long id, HttpServletRequest http) {
        FichierSepaGenere f = charger(id);
        exigerStatut(f, MassPaymentService.STATUT_SOUMIS, "validé");
        quatreYeux(f);
        f.setStatutFichier(MassPaymentService.STATUT_VALIDE);
        f.setDecisionPar(utilisateur());
        f.setDecisionLe(LocalDateTime.now());
        f.setMotifRejet(null);
        repository.save(f);
        auditService.log(AuditAction.FICHIER_VALIDE, "Fichier #" + id + " (" + f.getNomFichier() + ") validé", "SUCCES", http);
        return massPaymentService.resume(f);
    }

    public FichierResume rejeter(Long id, String motif, HttpServletRequest http) {
        if (motif == null || motif.isBlank()) {
            throw new IllegalArgumentException("Un motif est obligatoire pour rejeter un fichier.");
        }
        FichierSepaGenere f = charger(id);
        exigerStatut(f, MassPaymentService.STATUT_SOUMIS, "rejeté");
        quatreYeux(f);
        f.setStatutFichier(MassPaymentService.STATUT_REJETE);
        f.setDecisionPar(utilisateur());
        f.setDecisionLe(LocalDateTime.now());
        f.setMotifRejet(motif.trim());
        repository.save(f);
        auditService.log(AuditAction.FICHIER_REJETE,
                "Fichier #" + id + " (" + f.getNomFichier() + ") rejeté — motif : " + motif.trim(), "SUCCES", http);
        return massPaymentService.resume(f);
    }

    private FichierSepaGenere charger(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Le fichier SEPA n° " + id + " n'est plus disponible. Actualisez l'historique et sélectionnez un fichier existant."));
    }

    private void exigerStatut(FichierSepaGenere f, String attendu, String action) {
        String statut = MassPaymentService.statutDe(f);
        if (!attendu.equals(statut)) {
            throw new IllegalStateException("Ce fichier ne peut pas être " + action + " : son statut est « " + statut
                    + " » alors que « " + attendu + " » est requis.");
        }
    }

    private void quatreYeux(FichierSepaGenere f) {
        String courant = utilisateur();
        if (courant != null && (courant.equals(f.getGenerePar()) || courant.equals(f.getSoumisPar()))) {
            throw new IllegalStateException("Principe des quatre yeux : vous ne pouvez pas valider ni rejeter un fichier que vous avez vous-même généré ou soumis.");
        }
    }

    private static String utilisateur() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
