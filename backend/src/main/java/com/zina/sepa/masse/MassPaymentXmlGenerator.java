package com.zina.sepa.masse;

import com.zina.sepa.domain.entity.Transaction;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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

    public record Genere(String xml, String msgId, int nbTransactions, BigDecimal ctrlSum) {}

    public Genere generer(String namespace, String version, List<Transaction> transactions,
                          String nomDebiteur, String ibanDebiteur, String bicDebiteur,
                          String dateExecution, String periode) {
        boolean v09 = version != null && version.startsWith("001.001.09");
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
            text(doc, initgPty, "Nm", nettoyer(nomDebiteur, MAX_NM));

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
            if (v09) {
                Element reqd = child(doc, pmtInf, "ReqdExctnDt");
                text(doc, reqd, "Dt", dateExec);
            } else {
                text(doc, pmtInf, "ReqdExctnDt", dateExec);
            }
            Element dbtr = child(doc, pmtInf, "Dbtr");
            text(doc, dbtr, "Nm", nettoyer(nomDebiteur, MAX_NM));
            Element dbtrAcct = child(doc, pmtInf, "DbtrAcct");
            text(doc, child(doc, dbtrAcct, "Id"), "IBAN", iban(ibanDebiteur));
            Element dbtrAgt = child(doc, pmtInf, "DbtrAgt");
            Element finInstn = child(doc, dbtrAgt, "FinInstnId");
            text(doc, finInstn, v09 ? "BICFI" : "BIC", bic(bicDebiteur));
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
                if (t.getBic() != null && !t.getBic().isBlank()) {
                    Element cdtrAgt = child(doc, tx, "CdtrAgt");
                    text(doc, child(doc, cdtrAgt, "FinInstnId"), v09 ? "BICFI" : "BIC", bic(t.getBic()));
                }
                Element cdtr = child(doc, tx, "Cdtr");
                text(doc, cdtr, "Nm", nettoyer(t.getNomBeneficiaire(), MAX_NM));
                Element cdtrAcct = child(doc, tx, "CdtrAcct");
                text(doc, child(doc, cdtrAcct, "Id"), "IBAN", iban(t.getIban()));
                Element rmtInf = child(doc, tx, "RmtInf");
                text(doc, rmtInf, "Ustrd", nettoyer(libelle(t, periode), MAX_USTRD));
            }

            return new Genere(serialiser(doc), msgId, nbTx, ctrlSum);
        } catch (Exception e) {
            throw new IllegalStateException("Échec de génération du XML : " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------

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
