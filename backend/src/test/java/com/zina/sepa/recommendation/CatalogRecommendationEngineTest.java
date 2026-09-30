package com.zina.sepa.recommendation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogRecommendationEngineTest {

    private final CatalogRecommendationEngine engine = new CatalogRecommendationEngine();

    @Test
    void iban_invalide() {
        Anomalie a = Anomalie.builder().type(AnomalieType.IBAN_INVALIDE)
                .matricule("EMP1").iban("FR7630006000011234567890189").build();
        String r = engine.recommander(a);
        assertThat(r).contains("modulo 97").contains("EMP1").contains("FR7630****0189");
    }

    @Test
    void bic_invalide() {
        Anomalie a = Anomalie.builder().type(AnomalieType.BIC_INVALIDE).build();
        a.setBic("XXX");
        assertThat(engine.recommander(a)).contains("BIC XXX").contains("8 ou 11");
    }

    @Test
    void doublon_paiement() {
        Anomalie a = Anomalie.builder().type(AnomalieType.DOUBLON_PAIEMENT)
                .matricule("EMP1").periode("2026-08").transactionId(5L).build();
        a.setNb(2);
        assertThat(engine.recommander(a)).contains("payé 2 fois").contains("2026-08").contains("id 5");
    }

    @Test
    void iban_partage() {
        Anomalie a = Anomalie.builder().type(AnomalieType.IBAN_PARTAGE)
                .iban("FR7630006000011234567890189").build();
        a.setNb(2);
        a.setMatricules("EMP1, EMP2");
        assertThat(engine.recommander(a)).contains("2 salariés").contains("EMP1, EMP2");
    }

    @Test
    void montant_invalide() {
        Anomalie a = Anomalie.builder().type(AnomalieType.MONTANT_INVALIDE)
                .matricule("EMP1").montant(new BigDecimal("0")).build();
        assertThat(engine.recommander(a)).contains("strictement positif").contains("EMP1");
    }

    @Test
    void net_superieur_brut() {
        Anomalie a = Anomalie.builder().type(AnomalieType.NET_SUPERIEUR_BRUT).matricule("EMP1").build();
        a.setNet(new BigDecimal("100"));
        a.setBrut(new BigDecimal("50"));
        String r = engine.recommander(a);
        assertThat(r).contains("net").contains("brut").contains("EMP1");
    }

    @Test
    void montant_suspect() {
        Anomalie a = Anomalie.builder().type(AnomalieType.MONTANT_SUSPECT)
                .montant(new BigDecimal("7500")).build();
        a.setRaisons("Montant 3,7x la moyenne");
        assertThat(engine.recommander(a)).contains("7500").contains("primes");
    }

    @Test
    void erreur_xsd() {
        Anomalie a = Anomalie.builder().type(AnomalieType.ERREUR_XSD).build();
        a.setElementXsd("Nm");
        a.setMessageXsd("valeur trop longue");
        assertThat(engine.recommander(a)).contains("Élément Nm").contains("caractères autorisés SEPA");
    }

    @Test
    void ctrl_sum_incoherent() {
        Anomalie a = Anomalie.builder().type(AnomalieType.CTRL_SUM_INCOHERENT).build();
        assertThat(engine.recommander(a)).contains("somme de contrôle");
    }

    @Test
    void type_inconnu_renvoie_generique() {
        Anomalie a = Anomalie.builder().type("TYPE_QUI_NEXISTE_PAS").build();
        assertThat(engine.recommander(a)).contains("Vérifier les données de la fiche de paie");
    }

    @Test
    void anomalie_null_ne_leve_pas_exception() {
        assertThat(engine.recommander(null)).isNotBlank();
    }
}
