package com.zina.sepa.societe;

import com.zina.sepa.domain.entity.Societe;
import com.zina.sepa.repository.SocieteRepository;
import com.zina.sepa.service.BusinessControlService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Gestion des sociétés débitrices. L'IBAN complet ne quitte jamais le backend : les réponses ne renvoient que l'IBAN masqué.
 */
@Service
public class SocieteService {

    public record SocieteDto(Long id, String code, String nom, String ibanMasque, String bic, boolean actif) {}

    public record SocieteRequest(String code, String nom, String iban, String bic, Boolean actif) {}

    /** Données du débiteur utilisées pour générer le pain.001 (IBAN complet). */
    public record Debiteur(String nom, String iban, String bic) {}

    private final SocieteRepository repository;
    private final BusinessControlService controles;

    public SocieteService(SocieteRepository repository, BusinessControlService controles) {
        this.repository = repository;
        this.controles = controles;
    }

    @Transactional(readOnly = true)
    public List<SocieteDto> lister() {
        return repository.findAllByOrderByCodeAsc().stream().map(this::versDto).toList();
    }

    @Transactional
    public SocieteDto creer(SocieteRequest req) {
        String code = requis(req.code(), "Le code de la société est obligatoire.");
        if (repository.existsByCode(code)) {
            throw new IllegalArgumentException("Une société existe déjà avec le code « " + code + " ». Choisissez un autre code.");
        }
        Societe societe = Societe.builder().code(code).build();
        appliquer(societe, req);
        return versDto(repository.save(societe));
    }

    /** Le code n'est pas modifiable : il relie la société aux transactions (clientId). */
    @Transactional
    public SocieteDto modifier(Long id, SocieteRequest req) {
        Societe societe = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("La société n° " + id + " est introuvable. Actualisez la liste."));
        appliquer(societe, req);
        return versDto(repository.save(societe));
    }

    /** Débiteur à utiliser pour un code société, si la société existe et est active. */
    @Transactional(readOnly = true)
    public Optional<Debiteur> debiteurPour(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return repository.findByCode(code.trim())
                .filter(Societe::isActif)
                .map(s -> new Debiteur(s.getNom(), s.getIban(), s.getBic()));
    }

    @Transactional(readOnly = true)
    public List<String> codesActifs() {
        return repository.findAllByOrderByCodeAsc().stream()
                .filter(Societe::isActif).map(Societe::getCode).toList();
    }

    private void appliquer(Societe societe, SocieteRequest req) {
        String nom = requis(req.nom(), "Le nom de la société est obligatoire.");
        // En modification, l'IBAN n'étant jamais renvoyé en clair, un champ vide conserve l'IBAN actuel.
        boolean conserverIban = societe.getId() != null && (req.iban() == null || req.iban().isBlank());
        String iban = conserverIban ? societe.getIban() : normaliser(req.iban());
        if (!controles.ibanValide(iban)) {
            throw new IllegalArgumentException("L'IBAN de la société est invalide (contrôle modulo 97). Vérifiez la saisie.");
        }
        String bic = normaliser(req.bic());
        if (!controles.bicValide(bic)) {
            throw new IllegalArgumentException("Le BIC de la société est invalide (format ISO 9362 attendu, 8 ou 11 caractères).");
        }
        societe.setNom(nom);
        societe.setIban(iban);
        societe.setBic(bic);
        if (req.actif() != null) {
            societe.setActif(req.actif());
        }
    }

    private SocieteDto versDto(Societe s) {
        return new SocieteDto(s.getId(), s.getCode(), s.getNom(), masquer(s.getIban()), s.getBic(), s.isActif());
    }

    /** Affiche les 4 premiers et 4 derniers caractères de l'IBAN. */
    static String masquer(String iban) {
        if (iban == null || iban.length() <= 8) {
            return "****";
        }
        return iban.substring(0, 4) + " **** **** **** **** " + iban.substring(iban.length() - 4);
    }

    private static String normaliser(String valeur) {
        return valeur == null ? null : valeur.replaceAll("\\s", "").toUpperCase();
    }

    private static String requis(String valeur, String message) {
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return valeur.trim();
    }
}
