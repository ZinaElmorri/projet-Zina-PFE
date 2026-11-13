package com.zina.sepa.societe;

import com.zina.sepa.domain.entity.Societe;
import com.zina.sepa.repository.SocieteRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Crée les sociétés de démonstration si la table est vide. Les IBAN respectent le contrôle modulo 97.
 */
@Component
@Order(20)
public class SocieteSeeder implements CommandLineRunner {

    private final SocieteRepository repository;

    public SocieteSeeder(SocieteRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        repository.saveAll(List.of(
                societe("SOC-001", "Zina Industries SARL", "FR7630006000011234567890189", "AGRIFRPP"),
                societe("SOC-002", "Zina Services SAS", "DE89370400440532013000", "COBADEFFXXX")));
    }

    private static Societe societe(String code, String nom, String iban, String bic) {
        return Societe.builder().code(code).nom(nom).iban(iban).bic(bic).actif(true).build();
    }
}
