package com.zina.sepa.security;

import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RoleMigrationRunnerTest {

    JdbcTemplate jdbc;
    AuditService audit = mock(AuditService.class);

    @BeforeEach
    void base() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        jdbc = new JdbcTemplate(ds);
        jdbc.execute("CREATE TABLE utilisateurs (id BIGINT AUTO_INCREMENT PRIMARY KEY, username VARCHAR(50), role VARCHAR(50), actif BOOLEAN)");
        jdbc.execute("CREATE TABLE audit_logs (id BIGINT AUTO_INCREMENT PRIMARY KEY, action VARCHAR(50))");
        jdbc.execute("ALTER TABLE utilisateurs ADD CONSTRAINT utilisateurs_role_check CHECK (role IN ('ADMINISTRATEUR','GESTIONNAIRE_RH','VALIDATEUR','AUDITEUR'))");
        for (String[] u : new String[][]{{"admin", "ADMINISTRATEUR"}, {"rh", "GESTIONNAIRE_RH"},
                {"validateur", "VALIDATEUR"}, {"auditeur", "AUDITEUR"}}) {
            jdbc.update("INSERT INTO utilisateurs (username, role, actif) VALUES (?, ?, TRUE)", u[0], u[1]);
        }
    }

    @AfterEach
    void fermer() {
        jdbc.execute("DROP ALL OBJECTS");
    }

    private String role(String username) {
        return jdbc.queryForObject("SELECT role FROM utilisateurs WHERE username = ?", String.class, username);
    }

    private boolean actif(String username) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT actif FROM utilisateurs WHERE username = ?", Boolean.class, username));
    }

    @Test
    void migre_les_anciens_roles_et_desactive_les_auditeurs() {
        new RoleMigrationRunner(jdbc, audit).run();

        assertThat(role("admin")).isEqualTo("ADMIN");
        assertThat(role("rh")).isEqualTo("EXPERT_RH");
        assertThat(role("validateur")).isEqualTo("MANAGER");
        assertThat(role("auditeur")).isEqualTo("MANAGER");
        assertThat(actif("auditeur")).isFalse();
        assertThat(actif("admin") && actif("rh") && actif("validateur")).isTrue();
        verify(audit).log(eq(AuditAction.ROLES_MIGRES), anyString(), eq("SUCCES"), eq("systeme"), anyString());
    }

    @Test
    void deux_executions_donnent_le_meme_resultat_et_une_seule_trace() {
        RoleMigrationRunner runner = new RoleMigrationRunner(jdbc, audit);
        runner.run();
        List<String> apresPremiere = jdbc.queryForList("SELECT username || ':' || role || ':' || actif FROM utilisateurs ORDER BY id", String.class);

        runner.run();

        assertThat(jdbc.queryForList("SELECT username || ':' || role || ':' || actif FROM utilisateurs ORDER BY id", String.class))
                .isEqualTo(apresPremiere);
        assertThat(runner.migrer().values()).containsOnly(0);
        verify(audit, times(1)).log(eq(AuditAction.ROLES_MIGRES), anyString(), eq("SUCCES"), eq("systeme"), anyString());
    }

    @Test
    void la_contrainte_elargie_accepte_les_nouveaux_roles_et_refuse_les_valeurs_inconnues() {
        new RoleMigrationRunner(jdbc, audit).run();

        jdbc.update("INSERT INTO utilisateurs (username, role, actif) VALUES ('manager1', 'MANAGER', TRUE)");
        jdbc.update("INSERT INTO utilisateurs (username, role, actif) VALUES ('expert1', 'EXPERT_RH', TRUE)");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO utilisateurs (username, role, actif) VALUES ('x', 'PIRATE', TRUE)"))
                .isInstanceOf(Exception.class);
    }
}
