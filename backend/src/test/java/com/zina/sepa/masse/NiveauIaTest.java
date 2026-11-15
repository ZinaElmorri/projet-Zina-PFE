package com.zina.sepa.masse;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NiveauIaTest {

    @Test
    void niveau_selon_les_memes_bornes_que_le_niveau_de_risque_de_la_fiche() {
        assertThat(MassPaymentService.niveauIa(0.12)).isEqualTo("FAIBLE");
        assertThat(MassPaymentService.niveauIa(0.35)).isEqualTo("MOYEN");
        assertThat(MassPaymentService.niveauIa(0.59)).isEqualTo("MOYEN");
        assertThat(MassPaymentService.niveauIa(0.60)).isEqualTo("ELEVE");
        assertThat(MassPaymentService.niveauIa(0.95)).isEqualTo("ELEVE");
    }
}
