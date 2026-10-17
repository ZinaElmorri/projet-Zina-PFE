package com.zina.sepa.masse;

import com.zina.sepa.masse.dto.MasseDto.AnalyseXsd;
import com.zina.sepa.masse.dto.MasseDto.ElementXsd;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class XsdPain001ReaderTest {

    private final XsdPain001Reader reader = new XsdPain001Reader();

    static String xsdOfficiel() throws Exception {
        try (var in = XsdPain001ReaderTest.class.getResourceAsStream("/xsd/pain.001.001.03.xsd")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private ElementXsd chercher(ElementXsd e, String nom) {
        if (nom.equals(e.nom())) {
            return e;
        }
        if (e.enfants() != null) {
            for (ElementXsd enfant : e.enfants()) {
                ElementXsd trouve = chercher(enfant, nom);
                if (trouve != null) {
                    return trouve;
                }
            }
        }
        return null;
    }

    @Test
    void analyse_du_xsd_officiel() throws Exception {
        AnalyseXsd a = reader.analyser(xsdOfficiel());

        assertThat(a.version()).isEqualTo("001.001.03");
        assertThat(a.namespace()).contains("pain.001.001.03");
        assertThat(a.racine().nom()).isEqualTo("CstmrCdtTrfInitn");
        // GrpHdr est le premier élément de CstmrCdtTrfInitn.
        assertThat(a.racine().enfants().get(0).nom()).isEqualTo("GrpHdr");

        ElementXsd msgId = chercher(a.racine(), "MsgId");
        assertThat(msgId).isNotNull();
        assertThat(msgId.obligatoire()).isTrue();
        assertThat(msgId.maxLength()).isEqualTo(35);

        ElementXsd nm = chercher(a.racine(), "Nm");
        assertThat(nm).isNotNull();
        // Dans pain.001.001.03, Nm (PartyIdentification32) est Max140Text.
        assertThat(nm.maxLength()).isEqualTo(140);
    }

    @Test
    void xsd_vide_est_incompatible() {
        assertThatThrownBy(() -> reader.analyser(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CstmrCdtTrfInitn")
                .hasMessageContaining("schéma pain.001 complet");
    }

    @Test
    void xsd_mal_forme_indique_comment_corriger_le_fichier() {
        assertThatThrownBy(() -> reader.analyser("<CstmrCdtTrfInitn>"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Schéma XSD illisible")
                .hasMessageContaining("importez un schéma XML bien formé");
    }
}
