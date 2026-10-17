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
        void attributs_invalides_indiquent_le_nom_et_la_correction() {
                String xsd = """
                                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                                        <xs:element name="InstdAmt"><xs:complexType><xs:simpleContent>
                                                <xs:extension base="xs:decimal"><xs:attribute name="Ccy" type="xs:string" use="required"/></xs:extension>
                                        </xs:simpleContent></xs:complexType></xs:element>
                                </xs:schema>
                                """;
                List<ErreurXsd> manquant = collector.valider("<InstdAmt>100</InstdAmt>", xsd);
                assertThat(manquant).hasSize(1);
                assertThat(manquant.get(0).message()).contains("attribut obligatoire manquant", "Ccy", "Renseignez cet attribut");
                List<ErreurXsd> interdit = collector.valider("<InstdAmt Ccy=\"EUR\" Other=\"X\">100</InstdAmt>", xsd);
                assertThat(interdit).hasSize(1);
                assertThat(interdit.get(0).message()).contains("attribut non autorisé", "Other", "Supprimez cet attribut");
        }

        @Test
        void messages_de_contraintes_indiquent_la_correction_et_les_limites() {
                verifierMessage("Nm", "string", "<xs:maxLength value=\"3\"/>", "Alice", "Nom", "3 caractères maximum");
                verifierMessage("Nm", "string", "<xs:minLength value=\"2\"/>", "", "valeur manquante", "au moins 2 caractères");
                verifierMessage("Code", "string", "<xs:length value=\"2\"/>", "ABC", "exactement 2 caractères");
                verifierMessage("BICFI", "string", "<xs:pattern value=\"[A-Z]{8}\"/>", "BAD!", "Code BIC/SWIFT", "8 ou 11 caractères", "AGRIFRPP");
                verifierMessage("Code", "string", "<xs:pattern value=\"[A-Z]{2}\"/>", "12", "format XSD : [A-Z]{2}");
                verifierMessage("ChrgBr", "string", "<xs:enumeration value=\"SLEV\"/>", "OTHER", "Répartition des frais", "Choisissez parmi : SLEV");
                verifierMessage("Dt", "date", "", "2026-02-30", "date réelle", "AAAA-MM-JJ");
                verifierMessage("CreDtTm", "dateTime", "", "INVALID", "AAAA-MM-JJThh:mm:ss");
                verifierMessage("InstdAmt", "decimal", "", "12,50", "point comme séparateur", "1500.50");
                verifierMessage("NbOfTxs", "integer", "", "2.5", "nombre entier attendu");
                verifierMessage("InstdAmt", "decimal", "<xs:minInclusive value=\"0.01\"/>", "0", "supérieure ou égale à 0.01");
                verifierMessage("InstdAmt", "decimal", "<xs:maxExclusive value=\"100\"/>", "100", "strictement inférieure à 100");
                verifierMessage("InstdAmt", "decimal", "<xs:fractionDigits value=\"2\"/>", "12.345", "décimales", "2 maximum");
                verifierMessage("InstdAmt", "decimal", "<xs:totalDigits value=\"3\"/>", "1234", "nombre total de chiffres", "3 maximum");
        }

        private void verifierMessage(String element, String type, String contraintes, String valeur, String... attendus) {
                String xsd = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"" + element
                                + "\"><xs:simpleType><xs:restriction base=\"xs:" + type + "\">" + contraintes
                                + "</xs:restriction></xs:simpleType></xs:element></xs:schema>";
                List<ErreurXsd> erreurs = collector.valider("<" + element + ">" + valeur + "</" + element + ">", xsd);
                assertThat(erreurs).hasSize(1);
                assertThat(erreurs.get(0).message()).contains(attendus).doesNotContain("cvc-");
        }

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

                                assertThat(erreurs).hasSize(2);
                                assertThat(erreurs).allMatch(e -> e.ligne() > 0 && e.colonne() > 0);
                                assertThat(erreurs).anyMatch(e -> e.element().equals("MsgId") && e.message().contains("trop longue"));
                                assertThat(erreurs).anyMatch(e -> e.element().equals("IBAN") && e.message().contains("au format incorrect"));
                                assertThat(erreurs).noneMatch(e -> e.message().contains("cvc-"));
    }

                @Test
                void erreurs_distinctes_sur_deux_elements_de_meme_nom_ne_sont_pas_fusionnees() {
                                String xsd = """
                                                                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
                                                                        <xs:element name="Document"><xs:complexType><xs:sequence>
                                                                                <xs:element name="Code" maxOccurs="2"><xs:simpleType>
                                                                                        <xs:restriction base="xs:string"><xs:pattern value="[A-Z]{2}"/></xs:restriction>
                                                                                </xs:simpleType></xs:element>
                                                                        </xs:sequence></xs:complexType></xs:element>
                                                                </xs:schema>
                                                                """;
                                List<ErreurXsd> erreurs = collector.valider("<Document><Code>12</Code><Code>34</Code></Document>", xsd);

                                assertThat(erreurs).hasSize(2);
                                assertThat(erreurs).allMatch(erreur -> erreur.element().equals("Code"));
                                assertThat(erreurs.get(0).colonne()).isNotEqualTo(erreurs.get(1).colonne());
                }

                            @Test
                            void iban_vide_indique_le_compte_et_le_champ_a_corriger() throws Exception {
                                String xsd = XsdPain001ReaderTest.xsdOfficiel();
                                Transaction transaction = Transaction.builder().id(1L).employeId("EMP1").nomBeneficiaire("Alice")
                                        .iban("FR7630006000011234567890189").bic("AGRIFRPP").montant(new BigDecimal("2000.00"))
                                        .devise("EUR").build();
                                String xml = generator.generer("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03", "001.001.03",
                                        List.of(transaction), "Societe", "", "AGRIFRPP", "2026-10-03", "2026-08").xml();

                                List<ErreurXsd> erreurs = collector.valider(xml, xsd);

                                assertThat(erreurs).hasSize(1);
                                assertThat(erreurs.get(0).message()).contains("IBAN du compte débiteur manquant")
                                        .contains("Renseignez le champ IBAN débiteur");
                            }

                            @Test
                            void format_iban_beneficiaire_indique_le_format_attendu() throws Exception {
                                String xsd = XsdPain001ReaderTest.xsdOfficiel();
                                Transaction transaction = Transaction.builder().id(1L).employeId("EMP1").nomBeneficiaire("Alice")
                                        .iban("INVALID").bic("AGRIFRPP").montant(new BigDecimal("2000.00"))
                                        .devise("EUR").build();
                                String xml = generator.generer("urn:iso:std:iso:20022:tech:xsd:pain.001.001.03", "001.001.03",
                                        List.of(transaction), "Societe", "FR7630006000011234567890189", "AGRIFRPP", "2026-10-03", "2026-08").xml();

                                List<ErreurXsd> erreurs = collector.valider(xml, xsd);

                                assertThat(erreurs).hasSize(1);
                                assertThat(erreurs.get(0).message()).contains("IBAN du compte bénéficiaire au format incorrect")
                                        .contains("deux lettres de pays").contains("Exemple : FR");

                                List<ErreurXsd> erreursUneLigne = collector.valider(xml.replace("\n", ""), xsd);
                                assertThat(erreursUneLigne).hasSize(1);
                                assertThat(erreursUneLigne.get(0).message()).contains("compte bénéficiaire au format incorrect");
                            }

                @Test
                void xml_mal_forme_ne_repete_pas_la_meme_erreur_fatale() {
                                String xsd = "<xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"><xs:element name=\"Document\"/></xs:schema>";

                                List<ErreurXsd> erreurs = collector.valider("<Document>", xsd);

                                assertThat(erreurs).hasSize(1);
                                assertThat(erreurs.get(0).message()).contains("mal formé");
                }

                @Test
                void regroupe_les_diagnostics_structurels_et_simplifie_les_namespaces() {
                                String xsd = """
                                                                <xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
                                                                                                         targetNamespace="urn:test" xmlns="urn:test" elementFormDefault="qualified">
                                                                        <xs:element name="Date"><xs:complexType><xs:sequence>
                                                                                <xs:element name="Dt" type="xs:date"/>
                                                                        </xs:sequence></xs:complexType></xs:element>
                                                                </xs:schema>
                                                                """;

                                List<ErreurXsd> erreurs = collector.valider("<Date xmlns=\"urn:test\">2026-10-03</Date>", xsd);

                                assertThat(erreurs).hasSize(1);
                                assertThat(erreurs.get(0).element()).isEqualTo("Date");
                                assertThat(erreurs.get(0).message()).contains("Contenu textuel interdit").contains("attendu : Dt")
                                                                .doesNotContain("urn:test").doesNotContain("cvc-");
                }
}
