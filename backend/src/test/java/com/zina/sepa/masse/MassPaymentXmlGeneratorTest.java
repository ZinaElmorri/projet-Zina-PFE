package com.zina.sepa.masse;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.masse.MassPaymentXmlGenerator.Genere;
import com.zina.sepa.masse.dto.MasseDto.ErreurXsd;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MassPaymentXmlGeneratorTest {

    private final MassPaymentXmlGenerator generator = new MassPaymentXmlGenerator();
    private final XsdErrorCollector collector = new XsdErrorCollector();

    private static final String NS_V03 = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03";
    private static final String NS_V09 = "urn:iso:std:iso:20022:tech:xsd:pain.001.001.09";

    private Transaction tx(long id, String emp, String nom, String iban, String bic, String montant) {
        return Transaction.builder().id(id).employeId(emp).nomBeneficiaire(nom)
                .iban(iban).bic(bic).montant(new BigDecimal(montant)).devise("EUR").build();
    }

    @Test
    void nbtxs_ctrlsum_et_deux_decimales() {
        List<Transaction> txs = List.of(
                tx(1, "EMP1", "Alice", "FR7630006000011234567890189", "AGRIFRPP", "2000.00"),
                tx(2, "EMP2", "Bob", "FR7612345678901234567890123", "BNPAFRPP", "1500.50"));

        Genere g = generator.generer(NS_V03, "001.001.03", txs, "Ma Societe",
                "FR7630006000011234567890189", "AGRIFRPP", "2026-10-03", "2026-08");

        assertThat(g.nbTransactions()).isEqualTo(2);
        assertThat(g.ctrlSum()).isEqualByComparingTo("3500.50");
        assertThat(g.xml()).contains("<NbOfTxs>2</NbOfTxs>");
        assertThat(g.xml()).contains("<CtrlSum>3500.50</CtrlSum>");
    }

    @Test
    void v03_utilise_BIC_et_v09_utilise_BICFI() {
        List<Transaction> txs = List.of(tx(1, "EMP1", "Alice", "FR7630006000011234567890189", "AGRIFRPP", "2000.00"));

        String v03 = generator.generer(NS_V03, "001.001.03", txs, "Soc", "FR7630006000011234567890189", "AGRIFRPP", "2026-10-03", "2026-08").xml();
        String v09 = generator.generer(NS_V09, "001.001.09", txs, "Soc", "FR7630006000011234567890189", "AGRIFRPP", "2026-10-03", "2026-08").xml();

        assertThat(v03).contains("<BIC>").doesNotContain("<BICFI>");
        assertThat(v09).contains("<BICFI>").doesNotContain("<BIC>");
        assertThat(v09).contains("<ReqdExctnDt>").contains("<Dt>2026-10-03</Dt>");
    }

    @Test
    void translitteration_et_troncature() {
        String nomLong = "Émilie Achràf de la Tres Longue Denomination Sociale Qui Depasse Largement Soixante Dix";
        List<Transaction> txs = List.of(tx(1, "EMP1", nomLong, "FR7630006000011234567890189", "AGRIFRPP", "2000.00"));

        String xml = generator.generer(NS_V03, "001.001.03", txs, "Soc", "FR7630006000011234567890189", "AGRIFRPP", "2026-10-03", "2026-08").xml();

        assertThat(xml).contains("Emilie Achraf"); // accents supprimés
        assertThat(xml).doesNotContain("Émilie");
        // Le Nm bénéficiaire est tronqué à 70 caractères.
        String nmGenere = xml.replaceAll("(?s).*<Cdtr>\\s*<Nm>(.*?)</Nm>.*", "$1");
        assertThat(nmGenere.length()).isLessThanOrEqualTo(70);
    }

    @Test
    void xml_genere_est_valide_contre_le_xsd_officiel() throws Exception {
        String xsd = XsdPain001ReaderTest.xsdOfficiel();
        List<Transaction> txs = List.of(
                tx(1, "EMP1", "Alice Martin", "FR7630006000011234567890189", "AGRIFRPP", "2000.00"),
                tx(2, "EMP2", "Bruno Lefevre", "FR7612345678901234567890123", "BNPAFRPP", "1500.50"),
                tx(3, "EMP3", "Chloe Durand", "FR7630004000031234567890143", "SOGEFRPP", "2780.00"));

        Genere g = generator.generer(NS_V03, "001.001.03", txs, "Ma Societe SARL",
                "FR7630006000011234567890189", "AGRIFRPP", "2026-10-03", "2026-08");

        List<ErreurXsd> erreurs = collector.valider(g.xml(), xsd);
        assertThat(erreurs).as("erreurs: %s", erreurs).isEmpty();
    }
}
