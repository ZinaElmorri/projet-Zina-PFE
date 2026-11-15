package com.zina.sepa.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zina.sepa.ai.dto.ResultatDto;
import com.zina.sepa.ai.dto.MontantSuspectResponse;
import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.repository.TransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Qualité du modèle IA (lecture de resultats.json) et recommandation fournie par le module IA. */
@ExtendWith(MockitoExtension.class)
class QualiteModeleServiceTest {

    @Mock AiSupervisionService supervision;
    @Mock MontantSuspectClient client;
    @Mock TransactionRepository repository;

    private final AiProperties properties = new AiProperties();
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void la_date_d_entrainement_est_lue_dans_la_version_du_modele() {
        assertThat(QualiteModeleService.dateEntrainement("iforest-2026-09-25")).isEqualTo("2026-09-25");
        assertThat(QualiteModeleService.dateEntrainement("iforest-2026-11-14-221346")).isEqualTo("2026-11-14");
        assertThat(QualiteModeleService.dateEntrainement("modele-inconnu")).isNull();
        assertThat(QualiteModeleService.dateEntrainement(null)).isNull();
    }

    @Test
    void sans_fichier_de_resultats_la_page_reste_utilisable(@TempDir Path dossier) {
        properties.setBenchmarkFile(dossier.resolve("absent.json").toString());
        when(supervision.etat()).thenReturn(Map.of("modelVersion", "iforest-2026-09-25"));

        Map<String, Object> qualite = new QualiteModeleService(properties, supervision, mapper).qualite();

        assertThat(qualite.get("bancDisponible")).isEqualTo(false);
        assertThat(qualite.get("banc")).isNull();
        assertThat(((Map<?, ?>) qualite.get("modele")).get("dateEntrainement")).isEqualTo("2026-09-25");
    }

    @Test
    void seuls_les_agregats_du_banc_d_essai_sont_exposes(@TempDir Path dossier) throws Exception {
        Path fichier = dossier.resolve("resultats.json");
        Files.writeString(fichier, """
                {"jeuDeDonnees":{"nbFiches":73568,"nbAnomalies":1517,"societes":[{"nom":"Societe fictive"}],"parametres":{"x":1}},
                 "evaluation":{"configurations":{"en_service":{"metriques":{"f1":{"moyenne":0.42}}}},"seuilEnService":0.5},
                 "comparaison":{"approches":{"regles":{"nom":"Regles","metriques":{"f1":{"moyenne":0.29}},"rappelParType":{"a":1}}},"ablation":{"x":1}},
                 "candidat":{"statut":"candidat, non active"}}""");
        properties.setBenchmarkFile(fichier.toString());
        when(supervision.etat()).thenReturn(Map.of());

        Map<String, Object> qualite = new QualiteModeleService(properties, supervision, mapper).qualite();

        assertThat(qualite.get("bancDisponible")).isEqualTo(true);
        JsonNode banc = (JsonNode) qualite.get("banc");
        assertThat(banc.at("/evaluation/seuilEnService").asDouble()).isEqualTo(0.5);
        assertThat(banc.at("/jeuDeDonnees/nbFiches").asInt()).isEqualTo(73568);
        assertThat(banc.at("/jeuDeDonnees/societes").isMissingNode()).isTrue();
        assertThat(banc.at("/comparaison/regles/metriques/f1/moyenne").asDouble()).isEqualTo(0.29);
        assertThat(banc.at("/comparaison/regles/rappelParType").isMissingNode()).isTrue();
        assertThat(banc.at("/candidat/statut").asText()).isEqualTo("candidat, non active");
    }

    @Test
    void la_recommandation_du_module_ia_remplace_la_recommandation_generique() throws Exception {
        properties.setEnabled(true);
        Transaction t = new Transaction();
        t.setId(7L);
        t.setEmployeId("EMP7");
        t.setIban("FR7630006000011234567890189");
        t.setMontant(new BigDecimal("0"));
        lenient().when(repository.findByEmployeIdIn(any())).thenReturn(List.of(t));
        lenient().when(repository.save(any(Transaction.class))).thenAnswer(i -> i.getArgument(0));
        when(client.detect(any())).thenReturn(new MontantSuspectResponse("iforest-x", List.of(
                new ResultatDto(7L, 0.9, true, List.of("Montant nul ou négatif"), "Montant nul ou négatif",
                        "Corriger le montant."))));

        new AiAnomalyService(properties, client, repository).analyser(List.of(t));

        assertThat(t.getRecommandation()).isEqualTo("Corriger le montant.");
    }

    @Test
    void un_module_ia_plus_ancien_sans_nouveaux_champs_reste_compatible() throws Exception {
        ResultatDto d = mapper.readValue(
                "{\"ficheId\":1,\"score\":0.7,\"isAnomaly\":true,\"raisons\":[\"x\"]}", ResultatDto.class);
        assertThat(d.isAnomaly()).isTrue();
        assertThat(d.typeProbable()).isNull();
        ResultatDto complet = mapper.readValue(
                "{\"ficheId\":1,\"score\":0.7,\"isAnomaly\":true,\"raisons\":[],\"typeProbable\":\"T\",\"recommandation\":\"R\"}",
                ResultatDto.class);
        assertThat(complet.typeProbable()).isEqualTo("T");
        assertThat(complet.recommandation()).isEqualTo("R");
    }
}
