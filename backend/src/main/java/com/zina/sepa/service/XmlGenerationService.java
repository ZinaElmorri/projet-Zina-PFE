package com.zina.sepa.service;

import com.zina.sepa.domain.entity.Transaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Génération de fichiers de virement SEPA au format ISO 20022 (pain.001.001.09).
 */
@Service
public class XmlGenerationService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Produit un message pain.001 (Customer Credit Transfer Initiation) à partir des transactions.
     */
    public String genererPain001(List<Transaction> transactions, String nomDebiteur, String ibanDebiteur) {
        String msgId = "MSG-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        String creDtTm = LocalDateTime.now().format(ISO);
        int nbTx = transactions.size();
        BigDecimal total = transactions.stream()
                .map(Transaction::getMontant)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.09\">\n");
        sb.append("  <CstmrCdtTrfInitn>\n");
        sb.append("    <GrpHdr>\n");
        sb.append("      <MsgId>").append(msgId).append("</MsgId>\n");
        sb.append("      <CreDtTm>").append(creDtTm).append("</CreDtTm>\n");
        sb.append("      <NbOfTxs>").append(nbTx).append("</NbOfTxs>\n");
        sb.append("      <CtrlSum>").append(total).append("</CtrlSum>\n");
        sb.append("      <InitgPty><Nm>").append(escape(nomDebiteur)).append("</Nm></InitgPty>\n");
        sb.append("    </GrpHdr>\n");
        sb.append("    <PmtInf>\n");
        sb.append("      <PmtInfId>").append(msgId).append("-P1</PmtInfId>\n");
        sb.append("      <PmtMtd>TRF</PmtMtd>\n");
        sb.append("      <NbOfTxs>").append(nbTx).append("</NbOfTxs>\n");
        sb.append("      <CtrlSum>").append(total).append("</CtrlSum>\n");
        sb.append("      <Dbtr><Nm>").append(escape(nomDebiteur)).append("</Nm></Dbtr>\n");
        sb.append("      <DbtrAcct><Id><IBAN>").append(ibanDebiteur).append("</IBAN></Id></DbtrAcct>\n");

        for (Transaction t : transactions) {
            sb.append("      <CdtTrfTxInf>\n");
            sb.append("        <PmtId><EndToEndId>E2E-").append(t.getId()).append("</EndToEndId></PmtId>\n");
            sb.append("        <Amt><InstdAmt Ccy=\"").append(t.getDevise()).append("\">")
              .append(t.getMontant()).append("</InstdAmt></Amt>\n");
            if (t.getBic() != null && !t.getBic().isBlank()) {
                sb.append("        <CdtrAgt><FinInstnId><BICFI>").append(t.getBic())
                  .append("</BICFI></FinInstnId></CdtrAgt>\n");
            }
            sb.append("        <Cdtr><Nm>").append(escape(t.getNomBeneficiaire())).append("</Nm></Cdtr>\n");
            sb.append("        <CdtrAcct><Id><IBAN>").append(t.getIban()).append("</IBAN></Id></CdtrAcct>\n");
            sb.append("        <RmtInf><Ustrd>").append(escape(t.getLibelle())).append("</Ustrd></RmtInf>\n");
            sb.append("      </CdtTrfTxInf>\n");
        }

        sb.append("    </PmtInf>\n");
        sb.append("  </CstmrCdtTrfInitn>\n");
        sb.append("</Document>\n");
        return sb.toString();
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }
}
