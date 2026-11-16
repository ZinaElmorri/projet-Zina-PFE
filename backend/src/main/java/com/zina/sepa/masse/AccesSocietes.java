package com.zina.sepa.masse;

import com.zina.sepa.affectation.AffectationService;
import com.zina.sepa.domain.entity.Transaction;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Un Expert RH n'accède qu'aux sociétés qui lui sont affectées ; les autres rôles (Manager, Admin) voient
 * toutes les sociétés. Sans utilisateur authentifié (traitements internes, tests), aucune restriction.
 */
@Component
public class AccesSocietes {

    private static final String ROLE_EXPERT = "ROLE_EXPERT_RH";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private final AffectationService affectations;

    public AccesSocietes(AffectationService affectations) {
        this.affectations = affectations;
    }

    private Authentication authentification() {
        return SecurityContextHolder.getContext().getAuthentication();
    }

    private boolean estExpert(Authentication auth) {
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> ROLE_EXPERT.equals(a.getAuthority()));
    }

    /** L'administrateur ne consulte que la vue de supervision de l'historique (aucune donnée par salarié). */
    public boolean estAdmin() {
        Authentication auth = authentification();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> ROLE_ADMIN.equals(a.getAuthority()));
    }

    /** Sociétés auxquelles l'utilisateur est limité ; vide si aucune restriction (Manager, Admin, traitements internes). */
    public Optional<Set<String>> perimetre() {
        Authentication auth = authentification();
        if (!estExpert(auth)) {
            return Optional.empty();
        }
        return Optional.of(affectations.societesDe(auth.getName()));
    }

    /** Garde uniquement les sociétés accessibles à l'utilisateur courant. */
    public List<String> filtrer(List<String> societes) {
        Authentication auth = authentification();
        if (!estExpert(auth)) {
            return societes;
        }
        Set<String> autorisees = affectations.societesDe(auth.getName());
        return societes.stream().filter(autorisees::contains).toList();
    }

    /** Refuse l'accès d'un Expert RH à une société qui ne lui est pas affectée. */
    public void verifier(String societe) {
        Authentication auth = authentification();
        if (!estExpert(auth)) {
            return;
        }
        if (societe == null || societe.isBlank()) {
            throw new IllegalArgumentException("Sélectionnez une société : vous ne pouvez travailler que sur les sociétés qui vous sont affectées.");
        }
        if (!affectations.societesDe(auth.getName()).contains(societe.trim())) {
            throw new AccessDeniedException("La société « " + societe + " » ne vous est pas affectée. Demandez à votre Manager de vous l'affecter.");
        }
    }

    /** Vrai si la société est visible pour l'utilisateur courant (sans lever d'exception). */
    public boolean autorise(String societe) {
        Authentication auth = authentification();
        if (!estExpert(auth)) {
            return true;
        }
        return societe != null && affectations.societesDe(auth.getName()).contains(societe);
    }

    /** Garde les fiches des sociétés accessibles ; une fiche sans société n'est visible que hors périmètre Expert RH. */
    public List<Transaction> filtrerTransactions(List<Transaction> transactions) {
        Optional<Set<String>> perimetre = perimetre();
        if (perimetre.isEmpty()) {
            return transactions;
        }
        Set<String> autorisees = perimetre.get();
        return transactions.stream().filter(t -> t.getClientId() != null && autorisees.contains(t.getClientId())).toList();
    }
}
