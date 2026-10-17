package com.zina.sepa.masse;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.masse.dto.MasseDto.AnalyseXsd;
import com.zina.sepa.masse.dto.MasseDto.ElementXsd;
import com.zina.sepa.masse.dto.MasseDto.ErreurXsd;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Génère un message SEPA pain.001 via l'API DOM, en suivant la version du XSD
 * (001.001.03 → &lt;BIC&gt; / date simple ; 001.001.09 → &lt;BICFI&gt; / &lt;Dt&gt;).
 */
@Component
public class MassPaymentXmlGenerator {

    private static final int MAX_MSG_ID = 35;
    private static final int MAX_NM = 70;
    private static final int MAX_USTRD = 140;
    private static final int MAX_E2E = 35;
    private static final DateTimeFormatter CRE_DT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    public record ReperePaiement(String matricule, int paiement, int iban, int bic, int montant) {}
    private record Omission(Element parent, String message) {}
    private record RepereDom(String matricule, Element paiement, Element iban, Element bic, Element montant) {}

    public record Genere(String xml, String msgId, int nbTransactions, BigDecimal ctrlSum,
                         List<ErreurXsd> avertissements, List<ReperePaiement> reperes) {
        public Genere(String xml, String msgId, int nbTransactions, BigDecimal ctrlSum) {
            this(xml, msgId, nbTransactions, ctrlSum, List.of(), List.of());
        }
    }

    public Genere generer(String namespace, String version, List<Transaction> transactions,
                          String nomDebiteur, String ibanDebiteur, String bicDebiteur,
                          String dateExecution, String periode) {
        return generer(namespace, version, transactions, nomDebiteur, ibanDebiteur, bicDebiteur,
            dateExecution, periode, null);
        }

        public Genere generer(String namespace, String version, List<Transaction> transactions,
                  String nomDebiteur, String ibanDebiteur, String bicDebiteur,
                  String dateExecution, String periode, AnalyseXsd analyse) {
        Map<String, ElementXsd> elements = new HashMap<>();
        if (analyse != null) indexer(analyse.racine(), elements);
        List<Omission> omissions = new ArrayList<>();
        List<RepereDom> reperesDom = new ArrayList<>();
        boolean formatRecent = version != null && version.matches("001\\.001\\.(09|10|11)");
        String ns = (namespace == null || namespace.isBlank())
                ? "urn:iso:std:iso:20022:tech:xsd:pain.001.001.03" : namespace;

        String msgId = tronquer("MSG-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase(), MAX_MSG_ID);
        String dateExec = (dateExecution == null || dateExecution.isBlank())
                ? LocalDate.now().plusDays(2).toString() : dateExecution;

        int nbTx = transactions.size();
        BigDecimal ctrlSum = transactions.stream()
                .map(t -> t.getMontant() == null ? BigDecimal.ZERO : t.getMontant())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().newDocument();

            Element document = doc.createElementNS(ns, "Document");
            doc.appendChild(document);
            Element racine = child(doc, document, "CstmrCdtTrfInitn");

            // --- GrpHdr ---
            Element grpHdr = child(doc, racine, "GrpHdr");
            text(doc, grpHdr, "MsgId", msgId);
            text(doc, grpHdr, "CreDtTm", LocalDateTime.now().format(CRE_DT));
            text(doc, grpHdr, "NbOfTxs", String.valueOf(nbTx));
            text(doc, grpHdr, "CtrlSum", ctrlSum.toPlainString());
            Element initgPty = child(doc, grpHdr, "InitgPty");
            textOptionnel(doc, initgPty, "Nm", nettoyer(nomDebiteur, MAX_NM), elements, omissions);

            // --- PmtInf ---
            Element pmtInf = child(doc, racine, "PmtInf");
            text(doc, pmtInf, "PmtInfId", tronquer(msgId + "-P1", MAX_MSG_ID));
            text(doc, pmtInf, "PmtMtd", "TRF");
            text(doc, pmtInf, "BtchBookg", "true");
            text(doc, pmtInf, "NbOfTxs", String.valueOf(nbTx));
            text(doc, pmtInf, "CtrlSum", ctrlSum.toPlainString());
            Element pmtTpInf = child(doc, pmtInf, "PmtTpInf");
            text(doc, child(doc, pmtTpInf, "SvcLvl"), "Cd", "SEPA");
            text(doc, child(doc, pmtTpInf, "CtgyPurp"), "Cd", "SALA");
            if (formatRecent) {
                Element reqd = child(doc, pmtInf, "ReqdExctnDt");
                text(doc, reqd, "Dt", dateExec);
            } else {
                text(doc, pmtInf, "ReqdExctnDt", dateExec);
            }
            Element dbtr = child(doc, pmtInf, "Dbtr");
            textOptionnel(doc, dbtr, "Nm", nettoyer(nomDebiteur, MAX_NM), elements, omissions);
            Element dbtrAcct = child(doc, pmtInf, "DbtrAcct");
            text(doc, child(doc, dbtrAcct, "Id"), "IBAN", iban(ibanDebiteur));
            Element dbtrAgt = child(doc, pmtInf, "DbtrAgt");
            Element finInstn = child(doc, dbtrAgt, "FinInstnId");
            textOptionnel(doc, finInstn, formatRecent ? "BICFI" : "BIC", bic(bicDebiteur), elements, omissions);
            text(doc, pmtInf, "ChrgBr", "SLEV");

            // --- Transactions ---
            for (Transaction t : transactions) {
                Element tx = child(doc, pmtInf, "CdtTrfTxInf");
                Element pmtId = child(doc, tx, "PmtId");
                text(doc, pmtId, "EndToEndId", endToEnd(t, periode));
                Element amt = child(doc, tx, "Amt");
                Element instd = doc.createElementNS(ns, "InstdAmt");
                instd.setAttribute("Ccy", t.getDevise() == null ? "EUR" : t.getDevise());
                instd.setTextContent(montant(t.getMontant()));
                amt.appendChild(instd);
                Element bicBeneficiaire = null;
                if (t.getBic() != null && !t.getBic().isBlank()) {
                    Element cdtrAgt = child(doc, tx, "CdtrAgt");
                    bicBeneficiaire = child(doc, child(doc, cdtrAgt, "FinInstnId"), formatRecent ? "BICFI" : "BIC");
                    bicBeneficiaire.setTextContent(bic(t.getBic()));
                }
                Element cdtr = child(doc, tx, "Cdtr");
                textOptionnel(doc, cdtr, "Nm", nettoyer(t.getNomBeneficiaire(), MAX_NM), elements, omissions);
                Element cdtrAcct = child(doc, tx, "CdtrAcct");
                Element ibanBeneficiaire = child(doc, child(doc, cdtrAcct, "Id"), "IBAN");
                ibanBeneficiaire.setTextContent(iban(t.getIban()));
                Element rmtInf = child(doc, tx, "RmtInf");
                text(doc, rmtInf, "Ustrd", nettoyer(libelle(t, periode), MAX_USTRD));
                reperesDom.add(new RepereDom(t.getEmployeId(), tx, ibanBeneficiaire, bicBeneficiaire, instd));
            }

            String xml = serialiser(doc);
            Map<Element, Integer> lignes = localiser(doc, xml);
            List<ErreurXsd> avertissements = omissions.stream().map(omission ->
                    new ErreurXsd(lignes.getOrDefault(omission.parent(), 0), 0,
                            omission.message(), omission.parent().getLocalName())).toList();
            List<ReperePaiement> reperes = reperesDom.stream().map(repere ->
                    new ReperePaiement(repere.matricule(), lignes.getOrDefault(repere.paiement(), 0),
                            lignes.getOrDefault(repere.iban(), 0), lignes.getOrDefault(repere.bic(), 0),
                            lignes.getOrDefault(repere.montant(), 0))).toList();
            return new Genere(xml, msgId, nbTx, ctrlSum, avertissements, reperes);
        } catch (Exception e) {
            throw new IllegalStateException("Génération du XML impossible. Vérifiez la date d'exécution, les données des paiements sélectionnés et la compatibilité du schéma XSD, puis relancez la génération.", e);
        }
    }

    // ------------------------------------------------------------------

    private void indexer(ElementXsd element, Map<String, ElementXsd> elements) {
        if (element == null) return;
        elements.put(element.chemin(), element);
        if (element.enfants() != null) element.enfants().forEach(enfant -> indexer(enfant, elements));
    }

    private void textOptionnel(Document doc, Element parent, String nom, String valeur,
                               Map<String, ElementXsd> elements, List<Omission> omissions) {
        List<String> chemin = new ArrayList<>();
        for (org.w3c.dom.Node noeud = parent; noeud instanceof Element; noeud = noeud.getParentNode()) {
            chemin.add(0, noeud.getLocalName());
        }
        if (!chemin.isEmpty() && chemin.get(0).equals("Document")) chemin.remove(0);
        String cheminComplet = String.join("/", chemin) + "/" + nom;
        ElementXsd definition = elements.get(cheminComplet);
        if (valeur.isBlank() && definition != null && !definition.obligatoire()) {
            String champ = nom.equals("Nm")
                    ? switch (parent.getLocalName()) {
                        case "InitgPty" -> "Nom de l'initiateur du paiement";
                        case "Dbtr" -> "Nom du débiteur";
                        case "Cdtr" -> "Nom du bénéficiaire";
                        default -> "Nom";
                    }
                    : "Code BIC/SWIFT de la banque du débiteur";
            omissions.add(new Omission(parent, champ + " non renseigné : balise optionnelle <" + nom
                    + "> omise. Ce champ n'est pas obligatoire dans le schéma sélectionné et son absence ne bloque pas la conformité XSD. "
                    + "Renseignez-le si vous souhaitez l'inclure dans le XML. Chemin : " + cheminComplet + "."));
            return;
        }
        text(doc, parent, nom, valeur);
    }

    private Map<Element, Integer> localiser(Document doc, String xml) throws Exception {
        Map<Element, Integer> lignes = new IdentityHashMap<>();
        var noeuds = doc.getElementsByTagNameNS("*", "*");
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        var lecteur = factory.createXMLStreamReader(new StringReader(xml));
        int index = 0;
        try {
            while (lecteur.hasNext()) {
                if (lecteur.next() == XMLStreamConstants.START_ELEMENT) {
                    lignes.put((Element) noeuds.item(index++), lecteur.getLocation().getLineNumber());
                }
            }
        } finally {
            lecteur.close();
        }
        return lignes;
    }

    private Element child(Document doc, Element parent, String name) {
        Element e = doc.createElementNS(parent.getNamespaceURI(), name);
        parent.appendChild(e);
        return e;
    }

    private void text(Document doc, Element parent, String name, String value) {
        Element e = doc.createElementNS(parent.getNamespaceURI(), name);
        e.setTextContent(value);
        parent.appendChild(e);
    }

    private String endToEnd(Transaction t, String periode) {
        String p = periode == null ? "" : periode.replace("-", "");
        return tronquer("E2E-" + safe(t.getEmployeId()) + "-" + p, MAX_E2E);
    }

    private String libelle(Transaction t, String periode) {
        String mmAAAA = periode != null && periode.length() == 7
                ? periode.substring(5) + "/" + periode.substring(0, 4) : "";
        return "Salaire " + mmAAAA + " - " + safe(t.getEmployeId());
    }

    private String montant(BigDecimal m) {
        return (m == null ? BigDecimal.ZERO : m).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String iban(String iban) {
        return iban == null ? "" : iban.replaceAll("\\s", "").toUpperCase();
    }

    private String bic(String bic) {
        return bic == null ? "" : bic.replaceAll("\\s", "").toUpperCase();
    }

    private String safe(String s) {
        return s == null ? "" : s;
    }

    /** Translittère les accents, supprime les caractères hors jeu SEPA, puis tronque. */
    private String nettoyer(String valeur, int maxLength) {
        if (valeur == null) {
            return "";
        }
        String sansAccents = Normalizer.normalize(valeur, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        String sepa = sansAccents.replaceAll("[^A-Za-z0-9/\\-?:().,'+ ]", " ")
                .replaceAll("\\s+", " ").trim();
        return tronquer(sepa, maxLength);
    }

    private String tronquer(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max);
    }

    private String serialiser(Document doc) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD, "");
        tf.setAttribute(javax.xml.XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(doc), new StreamResult(writer));
        return writer.toString();
    }
}
