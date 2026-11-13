package com.zina.sepa.affectation;

import com.zina.sepa.domain.entity.Societe;
import com.zina.sepa.domain.entity.User;
import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.domain.enums.Role;
import com.zina.sepa.repository.SocieteRepository;
import com.zina.sepa.repository.UserRepository;
import com.zina.sepa.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Affectation des sociétés aux Experts RH, avec historique. */
@Service
public class AffectationService {

    public record AffectationDto(String societeCode, String societeNom, Long expertId, String expertUsername,
                                 String expertNom, String depuis) {}

    public record ExpertDto(Long id, String username, String nomComplet) {}

    public record HistoriqueDto(String societeCode, String expertUsername, String debutLe, String finLe,
                                String affectePar) {}

    private final AffectationRepository repository;
    private final SocieteRepository societeRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;

    public AffectationService(AffectationRepository repository, SocieteRepository societeRepository,
                              UserRepository userRepository, AuditService auditService) {
        this.repository = repository;
        this.societeRepository = societeRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /** Une ligne par société, avec son expert actuel s'il y en a un. */
    @Transactional(readOnly = true)
    public List<AffectationDto> lister() {
        Map<String, AffectationSociete> actives = repository.findByFinLeIsNull().stream()
                .collect(Collectors.toMap(AffectationSociete::getSocieteCode, Function.identity(), (a, b) -> a));
        return societeRepository.findAllByOrderByCodeAsc().stream().map(societe -> {
            AffectationSociete a = actives.get(societe.getCode());
            if (a == null) {
                return new AffectationDto(societe.getCode(), societe.getNom(), null, null, null, null);
            }
            String nomExpert = a.getExpertId() == null ? null
                    : userRepository.findById(a.getExpertId()).map(User::getNomComplet).orElse(null);
            return new AffectationDto(societe.getCode(), societe.getNom(), a.getExpertId(), a.getExpertUsername(),
                    nomExpert, format(a.getDebutLe()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public List<ExpertDto> experts() {
        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == Role.EXPERT_RH && u.isActif())
                .sorted(Comparator.comparing(User::getUsername))
                .map(u -> new ExpertDto(u.getId(), u.getUsername(), u.getNomComplet()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HistoriqueDto> historique(String societeCode) {
        return repository.findBySocieteCodeOrderByDebutLeDescIdDesc(societeCode).stream()
                .map(a -> new HistoriqueDto(a.getSocieteCode(), a.getExpertUsername(), format(a.getDebutLe()),
                        format(a.getFinLe()), a.getAffectePar()))
                .toList();
    }

    /** Affecte la société à l'expert ; l'affectation précédente est clôturée et reste dans l'historique. */
    @Transactional
    public AffectationDto affecter(String societeCode, Long expertId, HttpServletRequest http) {
        Societe societe = societeRepository.findByCode(societeCode)
                .orElseThrow(() -> new IllegalArgumentException("La société « " + societeCode + " » est introuvable. Actualisez la liste."));
        User expert = userRepository.findById(expertId)
                .filter(u -> u.getRole() == Role.EXPERT_RH && u.isActif())
                .orElseThrow(() -> new IllegalArgumentException("Cet utilisateur n'est pas un Expert RH actif : choisissez un expert dans la liste."));

        Optional<AffectationSociete> courante = repository.findBySocieteCodeAndFinLeIsNull(societeCode);
        if (courante.isPresent() && expert.getId().equals(courante.get().getExpertId())) {
            return lister().stream().filter(a -> a.societeCode().equals(societeCode)).findFirst().orElseThrow();
        }
        LocalDateTime maintenant = LocalDateTime.now();
        courante.ifPresent(a -> {
            a.setFinLe(maintenant);
            repository.save(a);
        });
        repository.save(AffectationSociete.builder().societeCode(societeCode).expertId(expert.getId())
                .expertUsername(expert.getUsername()).debutLe(maintenant).affectePar(utilisateur()).build());
        auditService.log(AuditAction.AFFECTATION_MODIFIEE,
                "Société " + societeCode + " affectée à " + expert.getUsername()
                        + courante.map(a -> " (ancien expert : " + a.getExpertUsername() + ")").orElse(""),
                "SUCCES", http);
        return new AffectationDto(societe.getCode(), societe.getNom(), expert.getId(), expert.getUsername(),
                expert.getNomComplet(), format(maintenant));
    }

    @Transactional
    public void retirer(String societeCode, HttpServletRequest http) {
        AffectationSociete courante = repository.findBySocieteCodeAndFinLeIsNull(societeCode)
                .orElseThrow(() -> new IllegalArgumentException("La société « " + societeCode + " » n'a pas d'expert affecté."));
        courante.setFinLe(LocalDateTime.now());
        repository.save(courante);
        auditService.log(AuditAction.AFFECTATION_MODIFIEE,
                "Société " + societeCode + " retirée à " + courante.getExpertUsername(), "SUCCES", http);
    }

    /** Codes des sociétés actuellement affectées à cet expert. */
    @Transactional(readOnly = true)
    public Set<String> societesDe(String username) {
        return repository.findByExpertUsernameAndFinLeIsNull(username).stream()
                .map(AffectationSociete::getSocieteCode).collect(Collectors.toSet());
    }

    private static String format(LocalDateTime date) {
        return date == null ? null : date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static String utilisateur() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }
}
