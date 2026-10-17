package com.zina.sepa.masse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zina.sepa.ai.AiAnomalyService;
import com.zina.sepa.ai.dto.AnalyseResult;
import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.entity.XsdSchema;
import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.masse.dto.MasseDto.*;
import com.zina.sepa.repository.XsdSchemaRepository;
import com.zina.sepa.service.AuditService;
import com.zina.sepa.service.BusinessControlService;
import com.zina.sepa.service.ValidationService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestration de la génération de virements de masse conformes SEPA (pain.001) :
 * filtrage, contrôles métier existants, génération DOM, validation XSD automatique,
 * historique et audit. Lecture des données existantes uniquement.
 */
@Service
public class MassPaymentService {

    private static final Set<TransactionStatus> STATUTS_AUTORISES =
            Set.of(TransactionStatus.APPROUVEE);
    private static final String SOURCE_IA = "IA";

    private final MassePaiementRepository paiementRepository;
    private final XsdSchemaRepository xsdSchemaRepository;
    private final BusinessControlService businessControlService;
    private final ValidationService validationService;
    private final AuditService auditService;
    private final XsdPain001Reader xsdReader;
    private final MassPaymentXmlGenerator generator;
    private final XsdErrorCollector errorCollector;
    private final FichierSepaGenereRepository fichierRepository;
    private final ObjectMapper objectMapper;
    private final AiAnomalyService aiAnomalyService;

    public MassPaymentService(MassePaiementRepository paiementRepository,
                              XsdSchemaRepository xsdSchemaRepository,
                              BusinessControlService businessControlService,
                              ValidationService validationService,
                              AuditService auditService,
                              XsdPain001Reader xsdReader,
                              MassPaymentXmlGenerator generator,
                              XsdErrorCollector errorCollector,
                              FichierSepaGenereRepository fichierRepository,
                              ObjectMapper objectMapper,
                              AiAnomalyService aiAnomalyService) {
        this.paiementRepository = paiementRepository;
        this.xsdSchemaRepository = xsdSchemaRepository;
        this.businessControlService = businessControlService;
        this.validationService = validationService;
        this.auditService = auditService;
        this.xsdReader = xsdReader;
        this.generator = generator;
        this.errorCollector = errorCollector;
        this.fichierRepository = fichierRepository;
        this.objectMapper = objectMapper;
        this.aiAnomalyService = aiAnomalyService;
    }

    // --- Filtres & paiements ---

    public FiltresResponse filtres(String clientId, String periode) {
        List<String> societes = paiementRepository.societes();

        List<String> periodes = paiementRepository.periodes(vide(clientId)).stream()
                .map(r -> String.format("%04d-%02d", ((Number) r[0]).intValue(), ((Number) r[1]).intValue()))
                .distinct()
                .sorted((a, b) -> b.compareTo(a))
                .collect(Collectors.toList());

        List<MatriculeDto> matricules = List.of();
        if (periode != null && !periode.isBlank()) {
            YearMonth ym = YearMonth.parse(periode);
            matricules = paiementRepository.matricules(vide(clientId), ym.atDay(1).atStartOfDay(),
                            ym.plusMonths(1).atDay(1).atStartOfDay()).stream()
                    .map(r -> new MatriculeDto((String) r[0], (String) r[1]))
                    .collect(Collectors.toList());
        }
        return new FiltresResponse(societes, periodes, matricules);
    }

    public PaiementsResponse paiements(String clientId, String periode, List<String> matricules, List<String> statuts) {
        return reponsePaiements(filtrer(clientId, periode, matricules, statuts));
    }

    /** Lance la détection IA sur la sélection ; les suspects restent signalés sans bloquer la génération. */
    public AnalyseIaMasse analyserIa(String clientId, String periode, List<String> matricules, List<String> statuts) {
        List<Transaction> transactions = filtrer(clientId, periode, matricules, statuts);
        AnalyseResult resultat = aiAnomalyService.analyser(transactions);
        PaiementsResponse paiements = reponsePaiements(transactions);
        int suspects = (int) paiements.paiements().stream().filter(PaiementMasse::suspectIa).count();
        return new AnalyseIaMasse(resultat.disponible(), resultat.modelVersion(), transactions.size(),
                suspects, paiements);
    }

    private PaiementsResponse reponsePaiements(List<Transaction> transactions) {
        List<PaiementMasse> lignes = transactions.stream().map(t -> {
            String err = erreurControle(t);
            boolean suspect = estSuspectIa(t);
            return new PaiementMasse(t.getId(), t.getEmployeId(), t.getNomBeneficiaire(),
                    masquer(t.getIban()), t.getBic(), t.getMontant(), t.getDevise(),
                    t.getStatut() == null ? null : t.getStatut().name(), err != null, err,
                    suspect, suspect ? t.getScoreRisque() : null, suspect ? t.getRaisonsAnomalie() : null);
        }).collect(Collectors.toList());
        BigDecimal total = transactions.stream()
                .map(t -> t.getMontant() == null ? BigDecimal.ZERO : t.getMontant())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PaiementsResponse(lignes, lignes.size(), total);
    }

    private static boolean estSuspectIa(Transaction t) {
        return SOURCE_IA.equals(t.getSourceAnomalie());
    }

    // --- XSD ---

    public List<XsdUtilisable> xsdUtilisables() {
        return xsdSchemaRepository.findAll().stream().map(s -> {
            XsdPain001Reader.MetaXsd meta = xsdReader.meta(s.getContenu());
            return new XsdUtilisable(s.getId(), s.getNom(), s.getVersion(),
                    meta.version(), meta.compatible(),
                    meta.compatible() ? null : "XSD non compatible pain.001 (CstmrCdtTrfInitn absent)");
        }).collect(Collectors.toList());
    }

    public AnalyseXsd analyse(Long xsdSchemaId) {
        return xsdReader.analyser(xsd(xsdSchemaId).getContenu());
    }

    // --- Aperçu / génération ---

    public ApercuResponse apercu(ApercuRequest req, HttpServletRequest http) {
        Resultat r = construire(req);
        auditService.log(AuditAction.XML_GENERE, detailAudit("Aperçu", req, r), "SUCCES", http);
        return r.reponse();
    }

    public FichierSepaGenere enregistrer(ApercuRequest req, HttpServletRequest http) {
        Resultat r = construire(req);
        if (!r.reponse().valideXsd()) {
            throw new IllegalStateException("Le XML n'est pas conforme au XSD : enregistrement interdit. Corrigez les erreurs rouges de l'aperçu, puis générez à nouveau le XML avant de l'enregistrer.");
        }
        XsdSchema schema = xsd(req.xsdSchemaId());
        String societe = req.clientId() == null ? "TOUTES" : req.clientId();
        String nomFichier = ("SEPA_" + societe + "_" + (req.periode() == null ? "" : req.periode())
                + "_" + r.msgId() + ".xml").replaceAll("[^A-Za-z0-9._-]", "_");

        FichierSepaGenere fichier = FichierSepaGenere.builder()
                .msgId(r.msgId())
                .nomFichier(nomFichier)
                .contenuXml(r.reponse().xml())
                .xsdSchemaId(schema.getId())
                .xsdNom(schema.getNom())
                .versionMessage(r.reponse().versionMessage())
                .clientId(req.clientId())
                .periode(req.periode())
                .matricules(r.transactions().stream().map(Transaction::getEmployeId).collect(Collectors.joining(",")))
                .transactionIds(r.transactions().stream().map(t -> String.valueOf(t.getId())).collect(Collectors.joining(",")))
                .nbTransactions(r.reponse().nbTransactions())
                .ctrlSum(r.reponse().ctrlSum())
                .nomDebiteur(req.nomDebiteur())
                .ibanDebiteur(req.ibanDebiteur())
                .dateExecution(req.dateExecution())
                .valideXsd(r.reponse().valideXsd())
                .erreursXsd(toJson(r.reponse().erreurs()))
                .generePar(utilisateur())
                .build();
        FichierSepaGenere sauve = fichierRepository.save(fichier);
        auditService.log(AuditAction.XML_GENERE, detailAudit("Enregistrement", req, r), "SUCCES", http);
        return sauve;
    }

    // --- Historique ---

    public List<FichierResume> historique(String clientId, String periode, Boolean valideXsd) {
        return fichierRepository.findAllByOrderByGenereLeDesc().stream()
                .filter(f -> clientId == null || clientId.isBlank() || clientId.equals(f.getClientId()))
                .filter(f -> periode == null || periode.isBlank() || periode.equals(f.getPeriode()))
                .filter(f -> valideXsd == null || valideXsd.equals(f.isValideXsd()))
                .map(this::resume)
                .collect(Collectors.toList());
    }

    public FichierDetail detail(Long id) {
        FichierSepaGenere f = fichierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Le fichier SEPA n° " + id + " n'est plus disponible. Actualisez l'historique et sélectionnez un fichier existant."));
        return new FichierDetail(resume(f), f.getContenuXml(), fromJson(f.getErreursXsd()),
                f.getNomDebiteur(), f.getIbanDebiteur(), f.getDateExecution());
    }

    public FichierSepaGenere pourTelecharger(Long id, HttpServletRequest http) {
        FichierSepaGenere f = fichierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Le fichier SEPA n° " + id + " n'est plus disponible. Actualisez l'historique et sélectionnez un fichier existant."));
        if (!f.isValideXsd()) {
            throw new IllegalStateException("Le XML n'est pas conforme au XSD : téléchargement interdit. Consultez les erreurs avec « Revoir », corrigez les données puis générez un nouveau fichier conforme.");
        }
        auditService.log(AuditAction.XML_GENERE,
                "Téléchargement fichier #" + id + " (" + f.getNomFichier() + ")", "SUCCES", http);
        return f;
    }

    // ------------------------------------------------------------------

    private record Resultat(ApercuResponse reponse, List<Transaction> transactions, String msgId) {}

    private Resultat construire(ApercuRequest req) {
        XsdSchema schema = xsd(req.xsdSchemaId());
        XsdPain001Reader.MetaXsd meta = xsdReader.meta(schema.getContenu());
        if (!meta.compatible()) {
            throw new IllegalArgumentException("Le schéma XSD sélectionné ne décrit pas un fichier de virements SEPA pain.001. Choisissez un schéma pain.001 compatible dans la liste des schémas.");
        }

        List<Transaction> transactions = selectionner(req);
        if (req.exclureErreurs()) {
            transactions = transactions.stream().filter(t -> erreurControle(t) == null).collect(Collectors.toList());
        }

        var controleMetier = businessControlService.controle(transactions);
        List<String> avertissements = new ArrayList<>(controleMetier.avertissements());
        transactions.stream().filter(MassPaymentService::estSuspectIa).forEach(t -> avertissements.add(
                "[emp " + t.getEmployeId() + "] Montant suspect (IA, score "
                        + String.format(java.util.Locale.FRANCE, "%.2f", t.getScoreRisque() == null ? 0.0 : t.getScoreRisque())
                        + ") : " + (t.getRaisonsAnomalie() == null || t.getRaisonsAnomalie().isBlank()
                        ? "montant inhabituel" : t.getRaisonsAnomalie())
                        + ". Vérifiez la fiche de paie avant d'envoyer le fichier."));

        MassPaymentXmlGenerator.Genere genere = generator.generer(meta.namespace(), meta.version(),
                transactions, req.nomDebiteur(), req.ibanDebiteur(), req.bicDebiteur(),
            req.dateExecution(), req.periode(), xsdReader.analyser(schema.getContenu()));

        List<ErreurXsd> erreurs = new ArrayList<>();
        boolean syntaxeValide = validationService.validerSyntaxe(genere.xml()).valide();
        erreurs.addAll(errorCollector.valider(genere.xml(), schema.getContenu()));
        if (!syntaxeValide && erreurs.isEmpty()) {
            erreurs.add(new ErreurXsd(0, 0, "XML mal formé : vérifiez les balises de fermeture et les caractères réservés, puis générez à nouveau le fichier.", ""));
        }
        boolean valide = erreurs.isEmpty();

        ApercuResponse reponse = new ApercuResponse(genere.xml(), valide, erreurs, controleMetier.erreurs(),
            avertissements,
            avertissementsXml(genere, avertissements),
            positionnerMetier(genere, controleMetier.erreurs(), false),
                genere.nbTransactions(), genere.ctrlSum(), meta.version(), genere.msgId());
        return new Resultat(reponse, transactions, genere.msgId());
    }

    private List<ErreurXsd> avertissementsXml(MassPaymentXmlGenerator.Genere genere, List<String> avertissements) {
        List<ErreurXsd> resultat = new ArrayList<>(genere.avertissements());
        resultat.addAll(positionnerMetier(genere, avertissements, true));
        return resultat;
    }

    private List<ErreurXsd> positionnerMetier(MassPaymentXmlGenerator.Genere genere, List<String> messages,
                                            boolean avertissement) {
        List<ErreurXsd> resultat = new ArrayList<>();
        for (var repere : genere.reperes()) {
            for (String message : messages) {
                if (!message.startsWith("[emp " + repere.matricule() + "]")) continue;
                int ligne = repere.paiement();
                String element = "CdtTrfTxInf";
                if (!avertissement) {
                    if (message.contains("IBAN") || message.contains("compte bancaire")) {
                        ligne = repere.iban();
                        element = "IBAN";
                    } else if (message.contains("BIC")) {
                        ligne = repere.bic();
                        element = "BIC";
                    } else {
                        ligne = repere.montant();
                        element = "InstdAmt";
                    }
                }
                resultat.add(new ErreurXsd(ligne, 0, message, element));
            }
        }
        return resultat;
    }

    private List<Transaction> selectionner(ApercuRequest req) {
        List<Transaction> transactions = req.transactionIds() != null && !req.transactionIds().isEmpty()
            ? paiementRepository.findAllById(req.transactionIds())
            : filtrer(req.clientId(), req.periode(), req.matricules(), req.statuts());
        List<Transaction> nonApprouvees = transactions.stream()
            .filter(transaction -> transaction.getStatut() != TransactionStatus.APPROUVEE).toList();
        if (!nonApprouvees.isEmpty()) {
            String references = nonApprouvees.stream()
                .map(transaction -> "n° " + transaction.getId() + " (" + transaction.getEmployeId() + ")")
                .collect(Collectors.joining(", "));
            throw new IllegalStateException("Génération refusée : seuls les paiements approuvés peuvent figurer dans le XML. "
                + "Paiements non approuvés : " + references + ". Faites approuver ces paiements, puis relancez la recherche et la génération.");
        }
        return transactions;
    }

    private List<Transaction> filtrer(String clientId, String periode, List<String> matricules, List<String> statuts) {
        Collection<TransactionStatus> statutsCibles = statuts(statuts);
        YearMonth ym = (periode == null || periode.isBlank()) ? YearMonth.now().minusMonths(1) : YearMonth.parse(periode);
        LocalDateTime debut = ym.atDay(1).atStartOfDay();
        LocalDateTime fin = ym.plusMonths(1).atDay(1).atStartOfDay();
        boolean sansMatricule = matricules == null || matricules.isEmpty();
        Collection<String> mats = sansMatricule ? List.of("") : matricules;
        return paiementRepository.filtrer(statutsCibles, debut, fin, vide(clientId), sansMatricule, mats);
    }

    private Collection<TransactionStatus> statuts(List<String> statuts) {
        if (statuts == null || statuts.isEmpty()) {
            return List.of(TransactionStatus.APPROUVEE);
        }
        List<TransactionStatus> result = new ArrayList<>();
        for (String s : statuts) {
            try {
                TransactionStatus st = TransactionStatus.valueOf(s.toUpperCase());
                if (STATUTS_AUTORISES.contains(st)) {
                    result.add(st);
                }
            } catch (IllegalArgumentException ignored) {
                // statut inconnu ignoré
            }
        }
        return result.isEmpty() ? List.of(TransactionStatus.APPROUVEE) : result;
    }

    /** Erreur de contrôle métier bloquante pour une fiche, via les méthodes existantes. */
    private String erreurControle(Transaction t) {
        if (t.getIban() == null || t.getIban().isBlank() || !businessControlService.ibanValide(t.getIban())) {
                return t.getIban() == null || t.getIban().isBlank()
                    ? "IBAN du bénéficiaire manquant : renseignez-le dans la fiche du paiement."
                    : "IBAN invalide : vérifiez le numéro de compte et sa clé de contrôle sur le relevé bancaire du bénéficiaire.";
        }
        if (t.getBic() != null && !t.getBic().isBlank() && !businessControlService.bicValide(t.getBic())) {
            return "BIC invalide : renseignez le code SWIFT de la banque du bénéficiaire avec 8 ou 11 caractères, sans espaces.";
        }
        if (t.getMontant() == null || t.getMontant().compareTo(BigDecimal.ZERO) <= 0) {
            return "Montant manquant, négatif ou nul : renseignez un montant strictement supérieur à zéro.";
        }
        return null;
    }

    private FichierResume resume(FichierSepaGenere f) {
        int nbErreurs = fromJson(f.getErreursXsd()).size();
        return new FichierResume(f.getId(), f.getNomFichier(), f.getClientId(), f.getPeriode(),
                f.getMatricules(), f.getNbTransactions(), f.getCtrlSum(), f.getXsdNom(), f.getVersionMessage(),
                f.isValideXsd(), nbErreurs, f.getGenerePar(),
                f.getGenereLe() == null ? null : f.getGenereLe().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private XsdSchema xsd(Long id) {
        return xsdSchemaRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Le schéma XSD n° " + id + " n'est plus disponible. Actualisez la liste et sélectionnez un schéma existant."));
    }

    private String detailAudit(String action, ApercuRequest req, Resultat r) {
        return action + " SEPA — société=" + (req.clientId() == null ? "toutes" : req.clientId())
                + ", période=" + req.periode() + ", nb=" + r.reponse().nbTransactions()
                + ", total=" + r.reponse().ctrlSum() + ", XSD=" + r.reponse().versionMessage()
                + ", conforme=" + r.reponse().valideXsd();
    }

    private String masquer(String iban) {
        if (iban == null) {
            return "";
        }
        String s = iban.replaceAll("\\s", "");
        return s.length() <= 10 ? s : s.substring(0, 6) + "****" + s.substring(s.length() - 4);
    }

    private String toJson(List<ErreurXsd> erreurs) {
        try {
            return objectMapper.writeValueAsString(erreurs);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<ErreurXsd> fromJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return List.of(objectMapper.readValue(json, ErreurXsd[].class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private String utilisateur() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? null : auth.getName();
    }

    private String vide(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
