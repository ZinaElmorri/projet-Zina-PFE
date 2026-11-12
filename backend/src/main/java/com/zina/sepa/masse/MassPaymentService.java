package com.zina.sepa.masse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zina.sepa.ai.AiAnomalyService;
import com.zina.sepa.ai.dto.AnalyseResult;
import com.zina.sepa.ai.dto.ResultatDto;
import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.entity.XsdSchema;
import com.zina.sepa.domain.enums.AuditAction;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.masse.dto.MasseDto.*;
import com.zina.sepa.repository.XsdSchemaRepository;
import com.zina.sepa.service.AuditService;
import com.zina.sepa.service.BusinessControlService;
import com.zina.sepa.service.ValidationService;
import com.zina.sepa.societe.SocieteService;
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
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    static final String STATUT_BROUILLON = "BROUILLON";
    static final String STATUT_NON_CONFORME = "NON_CONFORME";
    private static final Pattern MESSAGE_SALARIE = Pattern.compile("^\\[emp ([^\\]]*)]\\s*(.*)$", Pattern.DOTALL);

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
    private final SocieteService societeService;

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
                              AiAnomalyService aiAnomalyService,
                              SocieteService societeService) {
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
        this.societeService = societeService;
    }

    // --- Filtres & paiements ---

    public FiltresResponse filtres(String clientId, String periode) {
        List<String> societes = java.util.stream.Stream.concat(
                        societeService.codesActifs().stream(), paiementRepository.societes().stream())
                .distinct().sorted().collect(Collectors.toList());

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

        SocieteService.Debiteur debiteurFichier = resoudreDebiteur(req);
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
                .nomDebiteur(debiteurFichier.nom())
                .ibanDebiteur(debiteurFichier.iban())
                .dateExecution(req.dateExecution())
                .valideXsd(r.reponse().valideXsd())
                .erreursXsd(toJson(r.reponse().erreurs()))
                .statutFichier(STATUT_BROUILLON)
                .generePar(utilisateur())
                .build();
        FichierSepaGenere sauve = fichierRepository.save(fichier);
        auditService.log(AuditAction.XML_GENERE, detailAudit("Enregistrement", req, r), "SUCCES", http);
        return sauve;
    }

    /**
     * Parcours complet : contrôles métier, analyse IA, génération XML et validation XSD, puis
     * enregistrement du résultat. Contrairement à {@link #enregistrer}, un XML non conforme est
     * conservé pour que ses erreurs soient consultables ; seul son téléchargement reste interdit.
     */
    public FichierSepaGenere controler(ApercuRequest req, HttpServletRequest http) {
        String societe = req.clientId() == null ? "" : req.clientId().trim();
        if (societe.isEmpty()) {
            throw new IllegalArgumentException("Aucune société sélectionnée. Un fichier de virements ne concerne qu'une seule société débitrice : choisissez-en une.");
        }
        SocieteService.Debiteur debiteur = societeService.debiteurPour(societe)
                .orElseThrow(() -> new IllegalArgumentException("Les coordonnées bancaires de la société « " + societe
                        + " » sont absentes ou la société est inactive. Complétez-les dans « Sociétés débitrices » avant de lancer le contrôle."));

        List<Transaction> selection = selectionner(req);
        if (selection.isEmpty()) {
            throw new IllegalArgumentException("Aucun paiement sélectionné. Cochez au moins un paiement approuvé avant de lancer le contrôle.");
        }
        // L'IA ne bloque jamais : indisponible, elle est simplement signalée dans le résultat.
        AnalyseResult ia = aiAnomalyService.analyser(selection);
        Resultat r = construire(req);

        ResultatIa resultatIa = resultatIa(ia, r.transactions());
        ResultatMetier metier = resultatMetier(r);
        List<ErreurXsd> erreursXsd = r.reponse().erreurs();
        boolean conforme = r.reponse().valideXsd();
        XsdSchema schema = xsd(req.xsdSchemaId());
        String nomFichier = ("SEPA_" + societe + "_" + (req.periode() == null ? "" : req.periode())
                + "_" + r.msgId() + ".xml").replaceAll("[^A-Za-z0-9._-]", "_");

        FichierSepaGenere fichier = FichierSepaGenere.builder()
                .msgId(r.msgId())
                .nomFichier(nomFichier)
                .contenuXml(r.reponse().xml())
                .xsdSchemaId(schema.getId())
                .xsdNom(schema.getNom())
                .versionMessage(r.reponse().versionMessage())
                .clientId(societe)
                .periode(req.periode())
                .matricules(r.transactions().stream().map(Transaction::getEmployeId).collect(Collectors.joining(",")))
                .transactionIds(r.transactions().stream().map(t -> String.valueOf(t.getId())).collect(Collectors.joining(",")))
                .nbTransactions(r.reponse().nbTransactions())
                .ctrlSum(r.reponse().ctrlSum())
                .nomDebiteur(debiteur.nom())
                .ibanDebiteur(debiteur.iban())
                .dateExecution(req.dateExecution())
                .valideXsd(conforme)
                .erreursXsd(toJson(erreursXsd))
                .avertissementsXsd(toJson(r.avertissementsXsd()))
                .resultatControleMetier(toJsonObjet(metier))
                .resultatIa(toJsonObjet(resultatIa))
                .nbErreurs(erreursXsd.size() + metier.erreurs().size())
                .nbAvertissements(r.avertissementsXsd().size() + metier.avertissements().size())
                .nbSuspectsIa(resultatIa.disponible() ? resultatIa.nbSuspects() : null)
                .statutFichier(conforme ? STATUT_BROUILLON : STATUT_NON_CONFORME)
                .generePar(utilisateur())
                .build();
        FichierSepaGenere sauve = fichierRepository.save(fichier);
        auditService.log(AuditAction.XML_GENERE, detailAudit("Contrôle et génération", req, r), "SUCCES", http);
        return sauve;
    }

    /** Tout ce qu'il faut pour les sous-pages du résultat ; les fichiers antérieurs au parcours ont des sections vides. */
    public ResultatFichier resultat(Long id) {
        FichierSepaGenere f = fichierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Le fichier SEPA n° " + id + " n'est plus disponible. Actualisez l'historique et sélectionnez un fichier existant."));
        ResultatMetier metier = lireObjet(f.getResultatControleMetier(), ResultatMetier.class,
                new ResultatMetier(List.of(), List.of(), List.of(), List.of()));
        ResultatIa ia = lireObjet(f.getResultatIa(), ResultatIa.class, new ResultatIa(false, null, 0, 0, List.of()));
        return new ResultatFichier(resume(f), f.getContenuXml(), f.getNomDebiteur(), masquer(f.getIbanDebiteur()),
                f.getDateExecution(), fromJson(f.getErreursXsd()), fromJson(f.getAvertissementsXsd()), metier, ia);
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

    private record Resultat(ApercuResponse reponse, List<Transaction> transactions, String msgId,
                            List<ErreurXsd> avertissementsXsd, List<String> avertissementsMetier) {}

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

        SocieteService.Debiteur debiteur = resoudreDebiteur(req);
        MassPaymentXmlGenerator.Genere genere = generator.generer(meta.namespace(), meta.version(),
                transactions, debiteur.nom(), debiteur.iban(), debiteur.bic(),
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
        return new Resultat(reponse, transactions, genere.msgId(), List.copyOf(genere.avertissements()),
                List.copyOf(controleMetier.avertissements()));
    }

    private ResultatMetier resultatMetier(Resultat r) {
        Map<String, String> noms = r.transactions().stream()
                .filter(t -> t.getEmployeId() != null)
                .collect(Collectors.toMap(Transaction::getEmployeId,
                        t -> t.getNomBeneficiaire() == null ? "" : t.getNomBeneficiaire(), (a, b) -> a));
        return new ResultatMetier(
                lignesControle(r.reponse().erreursControleMetier(), noms),
                lignesControle(r.avertissementsMetier(), noms),
                r.reponse().erreursControleMetierXml(),
                r.reponse().avertissements());
    }

    /** Les messages métier existants commencent par « [emp matricule] » : on en tire le salarié concerné. */
    private List<LigneControle> lignesControle(List<String> messages, Map<String, String> noms) {
        return messages.stream().map(message -> {
            Matcher m = MESSAGE_SALARIE.matcher(message);
            if (!m.matches()) {
                return new LigneControle(null, null, message);
            }
            return new LigneControle(m.group(1), noms.get(m.group(1)), m.group(2));
        }).collect(Collectors.toList());
    }

    private ResultatIa resultatIa(AnalyseResult ia, List<Transaction> transactions) {
        if (!ia.disponible()) {
            return new ResultatIa(false, null, 0, 0, List.of());
        }
        Map<Long, ResultatDto> parFiche = ia.resultats().stream()
                .filter(d -> d.ficheId() != null)
                .collect(Collectors.toMap(ResultatDto::ficheId, d -> d, (a, b) -> a));
        List<LigneIa> lignes = transactions.stream().map(t -> {
            ResultatDto d = parFiche.get(t.getId());
            boolean suspect = d != null && d.isAnomaly();
            return new LigneIa(t.getId(), t.getEmployeId(), t.getNomBeneficiaire(), t.getMontant(),
                    d == null ? null : d.score(), suspect,
                    suspect && d.raisons() != null ? d.raisons() : List.of(),
                    suspect ? t.getRecommandation() : null);
        }).collect(Collectors.toList());
        int suspects = (int) lignes.stream().filter(LigneIa::suspect).count();
        return new ResultatIa(true, ia.modelVersion(), lignes.size(), suspects, lignes);
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

    /** Débiteur de la société sélectionnée ; à défaut, les champs saisis dans la requête. */
    private SocieteService.Debiteur resoudreDebiteur(ApercuRequest req) {
        return societeService.debiteurPour(req.clientId())
                .orElseGet(() -> new SocieteService.Debiteur(req.nomDebiteur(), req.ibanDebiteur(), req.bicDebiteur()));
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
        String statut = f.getStatutFichier() != null ? f.getStatutFichier()
                : (f.isValideXsd() ? STATUT_BROUILLON : STATUT_NON_CONFORME);
        return new FichierResume(f.getId(), f.getNomFichier(), f.getClientId(), f.getPeriode(),
                f.getMatricules(), f.getNbTransactions(), f.getCtrlSum(), f.getXsdNom(), f.getVersionMessage(),
                f.isValideXsd(), nbErreurs, f.getGenerePar(),
                f.getGenereLe() == null ? null : f.getGenereLe().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                statut, f.getNbErreurs(), f.getNbAvertissements(), f.getNbSuspectsIa());
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
        String json = toJsonObjet(erreurs);
        return json == null ? "[]" : json;
    }

    private String toJsonObjet(Object valeur) {
        try {
            return objectMapper.writeValueAsString(valeur);
        } catch (Exception e) {
            return null;
        }
    }

    private <T> T lireObjet(String json, Class<T> type, T parDefaut) {
        if (json == null || json.isBlank()) {
            return parDefaut;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return parDefaut;
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
