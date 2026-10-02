package com.zina.sepa.masse;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.masse.MassPaymentXmlGenerator.Genere;
import com.zina.sepa.masse.dto.MasseDto.ErreurXsd;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class XsdErrorCollectorTest {

    private final MassPaymentXmlGenerator generator = new MassPaymentXmlGenerator();
    private final XsdErrorCollector collector = new XsdErrorCollector();

    @Test
    void collecte_les_deux_erreurs_volontaires_avec_ligne() throws Exception {
        String xsd = XsdPain001ReaderTest.xsdOfficiel();
        Transaction t = Transaction.builder().id(1L).employeId("EMP1").nomBeneficiaire("Alice")
                .iban("FR7630006000011234567890189").bic("AGRIFRPP").montant(new BigDecimal("2000.00"))
                .devise("EUR").build();
        Genere g = generator.generer("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03", "001.001.03",
                List.of(t), "Soc", "FR7630006000011234567890189", "AGRIFRPP", "2026-10-03", "2026-08");

        // Erreur 1 : MsgId trop long (> 35). Erreur 2 : IBAN débiteur au format invalide.
        String xmlInvalide = g.xml()
                .replaceFirst("<MsgId>[^<]*</MsgId>", "<MsgId>" + "X".repeat(40) + "</MsgId>")
                .replaceFirst("<IBAN>FR7630006000011234567890189</IBAN>", "<IBAN>FR!!INVALID</IBAN>");

        List<ErreurXsd> erreurs = collector.valider(xmlInvalide, xsd);

        assertThat(erreurs).hasSizeGreaterThanOrEqualTo(2);
        assertThat(erreurs).anyMatch(e -> e.ligne() > 0);
        assertThat(erreurs).anyMatch(e -> e.message().toLowerCase().contains("trop longue")
                || e.message().toLowerCase().contains("format"));
    }
}
