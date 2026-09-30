package com.zina.sepa.config;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.entity.User;
import com.zina.sepa.domain.entity.XsdSchema;
import com.zina.sepa.domain.enums.RiskLevel;
import com.zina.sepa.domain.enums.Role;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.repository.TransactionRepository;
import com.zina.sepa.repository.UserRepository;
import com.zina.sepa.repository.XsdSchemaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Crée un jeu de données par défaut au démarrage si la base est vide :
 * comptes utilisateurs (un par rôle), transactions et schémas XSD de démonstration.
 * Mot de passe par défaut = &lt;username&gt;123 (à changer en production).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final XsdSchemaRepository xsdSchemaRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository,
                      TransactionRepository transactionRepository,
                      XsdSchemaRepository xsdSchemaRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.xsdSchemaRepository = xsdSchemaRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUsers();
        seedSchemas();
        seedTransactions();
    }

    private void seedUsers() {
        saveUserIfAbsent("admin", "Administrateur", "admin@sepa.local", Role.ADMINISTRATEUR);
        saveUserIfAbsent("rh", "Gestionnaire RH", "rh@sepa.local", Role.GESTIONNAIRE_RH);
        saveUserIfAbsent("validateur", "Validateur", "validateur@sepa.local", Role.VALIDATEUR);
        saveUserIfAbsent("auditeur", "Auditeur", "auditeur@sepa.local", Role.AUDITEUR);
    }

    private void saveUserIfAbsent(String username, String nomComplet, String email, Role role) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        userRepository.save(user(username, nomComplet, email, role));
    }

    private User user(String username, String nomComplet, String email, Role role) {
        return User.builder()
                .username(username)
                .password(passwordEncoder.encode(username + "123"))
                .nomComplet(nomComplet)
                .email(email)
                .role(role)
                .actif(true)
                .build();
    }

    private void seedSchemas() {
        if (xsdSchemaRepository.count() > 0) {
            return;
        }
        xsdSchemaRepository.save(XsdSchema.builder()
                .nom("pain.001.001.03")
                .version(1)
                .cheminFichier("/schemas/pain.001.001.03.xsd")
                .contenu("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>")
                .importePar("admin")
                .build());
        xsdSchemaRepository.save(XsdSchema.builder()
                .nom("pain.008.001.02")
                .version(1)
                .cheminFichier("/schemas/pain.008.001.02.xsd")
                .contenu("<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"/>")
                .importePar("admin")
                .build());
    }

    private void seedTransactions() {
        if (transactionRepository.count() > 0) {
            return;
        }
        transactionRepository.saveAll(List.of(
                transaction("EMP001", "Alice Martin", "FR7630006000011234567890189", "AGRIFRPP",
                        new BigDecimal("2450.00"), "Salaire mars", TransactionStatus.APPROUVEE, 0.12, RiskLevel.FAIBLE),
                transaction("EMP002", "Bruno Lefevre", "FR7612345678901234567890123", "BNPAFRPP",
                        new BigDecimal("3100.50"), "Salaire mars", TransactionStatus.APPROUVEE, 0.18, RiskLevel.FAIBLE),
                transaction("EMP003", "Chloe Durand", "FR7630004000031234567890143", "SOGEFRPP",
                        new BigDecimal("2780.00"), "Salaire mars", TransactionStatus.VALIDEE, 0.35, RiskLevel.MOYEN),
                transaction("EMP004", "David Petit", "FR7610011000201234567890188", "PSSTFRPP",
                        new BigDecimal("9800.00"), "Prime exceptionnelle", TransactionStatus.VALIDEE, 0.78, RiskLevel.ELEVE),
                transaction("EMP005", "Emma Roux", "FR7630002005500000157841Z25", "CRLYFRPP",
                        new BigDecimal("15200.00"), "Virement inhabituel", TransactionStatus.BROUILLON, 0.93, RiskLevel.CRITIQUE),
                transaction("EMP006", "Farid Benali", "FR7614508000301234567890162", "BSUIFRPP",
                        new BigDecimal("2620.00"), "Salaire mars", TransactionStatus.REJETEE, 0.55, RiskLevel.MOYEN)
        ));
    }

    private Transaction transaction(String employeId, String nom, String iban, String bic,
                                    BigDecimal montant, String libelle,
                                    TransactionStatus statut, double score, RiskLevel niveau) {
        return Transaction.builder()
                .employeId(employeId)
                .nomBeneficiaire(nom)
                .iban(iban)
                .bic(bic)
                .montant(montant)
                .devise("EUR")
                .libelle(libelle)
                .statut(statut)
                .scoreRisque(score)
                .niveauRisque(niveau)
                .build();
    }
}
