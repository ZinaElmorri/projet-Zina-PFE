package com.zina.sepa.security;

import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.domain.enums.Role;
import com.zina.sepa.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Migre les rôles existants vers ADMIN / MANAGER / EXPERT_RH au démarrage, avant tout autre runner.
 *
 * <p>Idempotent : une seconde exécution ne trouve plus de ligne à migrer. Hibernate (ddl-auto=update)
 * ne met pas à jour les contraintes CHECK des colonnes enum ; elles sont donc élargies ici pour accepter
 * les nouvelles valeurs de rôle et d'action d'audit. Aucune table ni colonne n'est supprimée.</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RoleMigrationRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RoleMigrationRunner.class);

    private final JdbcTemplate jdbc;
    private final AuditService auditService;

    public RoleMigrationRunner(JdbcTemplate jdbc, AuditService auditService) {
        this.jdbc = jdbc;
        this.auditService = auditService;
    }

    @Override
    public void run(String... args) {
        elargirContrainte("utilisateurs", "utilisateurs_role_check", "role",
                Arrays.stream(Role.values()).map(Enum::name).toList());
        elargirContrainte("audit_logs", "audit_logs_action_check", "action",
                Arrays.stream(AuditAction.values()).map(Enum::name).toList());

        Map<String, Integer> rapport = migrer();
        int total = rapport.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            log.info("Migration des rôles : rien à migrer.");
            return;
        }
        String detail = rapport.entrySet().stream()
                .filter(e -> e.getValue() > 0)
                .map(e -> e.getKey() + " : " + e.getValue())
                .collect(Collectors.joining(" ; "));
        log.info("Migration des rôles : {} compte(s) migré(s) ({}).", total, detail);
        auditService.log(AuditAction.ROLES_MIGRES, "Migration des rôles — " + detail, "SUCCES", "systeme", "localhost");
    }

    /** Retourne, pour chaque règle de migration, le nombre de comptes migrés. */
    Map<String, Integer> migrer() {
        Map<String, Integer> rapport = new LinkedHashMap<>();
        rapport.put("ADMINISTRATEUR → ADMIN", jdbc.update("UPDATE utilisateurs SET role = 'ADMIN' WHERE role = 'ADMINISTRATEUR'"));
        rapport.put("GESTIONNAIRE_RH → EXPERT_RH", jdbc.update("UPDATE utilisateurs SET role = 'EXPERT_RH' WHERE role = 'GESTIONNAIRE_RH'"));
        rapport.put("VALIDATEUR → MANAGER", jdbc.update("UPDATE utilisateurs SET role = 'MANAGER' WHERE role = 'VALIDATEUR'"));

        // Le rôle Auditeur disparaît : ses comptes sont désactivés en attendant la décision de l'administrateur.
        List<String> auditeurs = jdbc.queryForList("SELECT username FROM utilisateurs WHERE role = 'AUDITEUR'", String.class);
        rapport.put("AUDITEUR → MANAGER (compte désactivé)",
                jdbc.update("UPDATE utilisateurs SET role = 'MANAGER', actif = FALSE WHERE role = 'AUDITEUR'"));
        if (!auditeurs.isEmpty()) {
            log.warn("Comptes Auditeur désactivés, à réaffecter ou supprimer : {}", auditeurs);
        }
        return rapport;
    }

    private void elargirContrainte(String table, String contrainte, String colonne, List<String> valeurs) {
        String liste = valeurs.stream().map(v -> "'" + v + "'").collect(Collectors.joining(", "));
        jdbc.execute("ALTER TABLE " + table + " DROP CONSTRAINT IF EXISTS " + contrainte);
        jdbc.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + contrainte
                + " CHECK (" + colonne + " IN (" + liste + "))");
    }
}
