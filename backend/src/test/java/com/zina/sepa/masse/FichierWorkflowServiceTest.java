package com.zina.sepa.masse;

import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FichierWorkflowServiceTest {

    @Mock FichierSepaGenereRepository repository;
    @Mock MassPaymentService massPaymentService;
    @Mock AccesSocietes acces;
    @Mock AuditService auditService;

    @AfterEach
    void deconnecter() {
        SecurityContextHolder.clearContext();
    }

    private FichierWorkflowService service() {
        return new FichierWorkflowService(repository, massPaymentService, acces, auditService);
    }

    private void connecte(String utilisateur) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(utilisateur, "x", List.of()));
    }

    private FichierSepaGenere fichier(String statut, String generePar) {
        FichierSepaGenere f = FichierSepaGenere.builder().id(7L).nomFichier("SEPA_SOC-001.xml").clientId("SOC-001")
                .valideXsd(true).statutFichier(statut).generePar(generePar).build();
        when(repository.findById(7L)).thenReturn(Optional.of(f));
        return f;
    }

    @Test
    void l_expert_soumet_un_brouillon_conforme() {
        connecte("expert1");
        FichierSepaGenere f = fichier("BROUILLON", "expert1");

        service().soumettre(7L, null);

        assertThat(f.getStatutFichier()).isEqualTo("SOUMIS");
        assertThat(f.getSoumisPar()).isEqualTo("expert1");
        assertThat(f.getSoumisLe()).isNotNull();
        verify(acces).verifier("SOC-001");
        verify(auditService).log(eq(AuditAction.FICHIER_SOUMIS), anyString(), eq("SUCCES"), any());
    }

    @Test
    void un_fichier_non_conforme_ne_peut_pas_etre_soumis() {
        connecte("expert1");
        FichierSepaGenere f = fichier("NON_CONFORME", "expert1");
        f.setValideXsd(false);

        assertThatThrownBy(() -> service().soumettre(7L, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("conforme");
        verifyNoInteractions(auditService);
    }

    @Test
    void un_fichier_avec_erreur_metier_ne_peut_pas_etre_soumis() {
        connecte("expert1");
        FichierSepaGenere f = fichier("BROUILLON", "expert1");
        when(massPaymentService.nbErreursMetier(f)).thenReturn(1);

        assertThatThrownBy(() -> service().soumettre(7L, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("contrôle métier");
        assertThat(f.getStatutFichier()).isEqualTo("BROUILLON");
        verifyNoInteractions(auditService);
    }

    @Test
    void le_manager_valide_un_fichier_soumis_par_un_autre() {
        connecte("manager1");
        FichierSepaGenere f = fichier("SOUMIS", "expert1");
        f.setSoumisPar("expert1");

        service().valider(7L, null);

        assertThat(f.getStatutFichier()).isEqualTo("VALIDE");
        assertThat(f.getDecisionPar()).isEqualTo("manager1");
        verify(auditService).log(eq(AuditAction.FICHIER_VALIDE), anyString(), eq("SUCCES"), any());
    }

    @Test
    void quatre_yeux_celui_qui_a_genere_ou_soumis_ne_peut_ni_valider_ni_rejeter() {
        connecte("manager1");
        FichierSepaGenere f = fichier("SOUMIS", "manager1");
        f.setSoumisPar("expert1");
        assertThatThrownBy(() -> service().valider(7L, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("quatre yeux");

        f.setGenerePar("expert1");
        f.setSoumisPar("manager1");
        assertThatThrownBy(() -> service().rejeter(7L, "motif", null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("quatre yeux");
        assertThat(f.getStatutFichier()).isEqualTo("SOUMIS");
        verifyNoInteractions(auditService);
    }

    @Test
    void le_rejet_exige_un_motif_et_le_conserve() {
        connecte("manager1");
        FichierSepaGenere f = fichier("SOUMIS", "expert1");

        assertThatThrownBy(() -> service().rejeter(7L, "  ", null))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("motif");
        service().rejeter(7L, "IBAN à revoir", null);

        assertThat(f.getStatutFichier()).isEqualTo("REJETE");
        assertThat(f.getMotifRejet()).isEqualTo("IBAN à revoir");
        verify(auditService).log(eq(AuditAction.FICHIER_REJETE), anyString(), eq("SUCCES"), any());
    }

    @Test
    void seul_un_fichier_soumis_peut_etre_valide() {
        connecte("manager1");
        fichier("BROUILLON", "expert1");

        assertThatThrownBy(() -> service().valider(7L, null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("SOUMIS");
    }
}
