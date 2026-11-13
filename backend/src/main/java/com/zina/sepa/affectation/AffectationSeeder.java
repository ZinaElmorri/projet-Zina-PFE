package com.zina.sepa.affectation;

import com.zina.sepa.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** Affectations de démonstration (expert1 → SOC-001, expert2 → SOC-002) tant qu'aucune affectation n'existe. */
@Component
@Order(30)
public class AffectationSeeder implements CommandLineRunner {

    private final AffectationRepository repository;
    private final UserRepository userRepository;

    public AffectationSeeder(AffectationRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        affecter("SOC-001", "expert1");
        affecter("SOC-002", "expert2");
    }

    private void affecter(String societe, String username) {
        userRepository.findByUsername(username).ifPresent(expert -> repository.save(AffectationSociete.builder()
                .societeCode(societe).expertId(expert.getId()).expertUsername(username)
                .debutLe(LocalDateTime.now()).affectePar("systeme").build()));
    }
}
