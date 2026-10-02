package com.zina.sepa.masse;

import com.fasterxml.jackson.databind.ObjectMapper;
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
            Set.of(TransactionStatus.APPROUVEE, TransactionStatus.VALIDEE);

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

    public MassPaymentService(MassePaiementRepository paiementRepository,
                              XsdSchemaRepository xsdSchemaRepository,
                              BusinessControlService businessControlService,
                              ValidationService validationService,
                              AuditService auditService,
                              XsdPain001Reader xsdReader,
                              MassPaymentXmlGenerator generator,
                              XsdErrorCollector errorCollector,
                              FichierSepaGenereRepository fichierRepository,
                              ObjectMapper objectMapper) {
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
        List<Transaction> transactions = filtrer(clientId, periode, matricules, statuts);
        List<PaiementMasse> lignes = transactions.stream().map(t -> {
            String err = erreurControle(t);
            return new PaiementMasse(t.getId(), t.getEmployeId(), t.getNomBeneficiaire(),
                    masquer(t.getIban()), t.getBic(), t.getMontant(), t.getDevise(),
                    t.getStatut() == null ? null : t.getStatut().name(), err != null, err);
        }).collect(Collectors.toList());
        BigDecimal total = transactions.stream()
                .map(t -> t.getMontant() == null ? BigDecimal.ZERO : t.getMontant())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PaiementsResponse(lignes, lignes.size(), total);
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
                .orElseThrow(() -> new IllegalArgumentException("Fichier introuvable : " + id));
        return new FichierDetail(resume(f), f.getContenuXml(), fromJson(f.getErreursXsd()),
                f.getNomDebiteur(), f.getIbanDebiteur(), f.getDateExecution());
    }

    public FichierSepaGenere pourTelecharger(Long id, HttpServletRequest http) {
        FichierSepaGenere f = fichierRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Fichier introuvable : " + id));
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
            throw new IllegalArgumentException("Le XSD choisi n'est pas compatible pain.001.");
        }

        List<Transaction> transactions = selectionner(req);
        if (req.exclureErreurs()) {
            transactions = transactions.stream().filter(t -> erreurControle(t) == null).collect(Collectors.toList());
        }

        List<String> erreursMetier = businessControlService.controle(transactions).erreurs();

        MassPaymentXmlGenerator.Genere genere = generator.generer(meta.namespace(), meta.version(),
                transactions, req.nomDebiteur(), req.ibanDebiteur(), req.bicDebiteur(),
                req.dateExecution(), req.periode());

        List<ErreurXsd> erreurs = new ArrayList<>();
        if (!validationService.validerSyntaxe(genere.xml()).valide()) {
            erreurs.add(new ErreurXsd(0, 0, "XML mal formé", ""));
        }
        erreurs.addAll(errorCollector.valider(genere.xml(), schema.getContenu()));
        boolean valide = erreurs.isEmpty();

        ApercuResponse reponse = new ApercuResponse(genere.xml(), valide, erreurs, erreursMetier,
                genere.nbTransactions(), genere.ctrlSum(), meta.version(), genere.msgId());
        return new Resultat(reponse, transactions, genere.msgId());
    }

    private List<Transaction> selectionner(ApercuRequest req) {
        if (req.transactionIds() != null && !req.transactionIds().isEmpty()) {
            return paiementRepository.findAllById(req.transactionIds());
        }
        return filtrer(req.clientId(), req.periode(), req.matricules(), req.statuts());
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
            return "IBAN invalide";
        }
        if (t.getBic() != null && !t.getBic().isBlank() && !businessControlService.bicValide(t.getBic())) {
            return "BIC invalide";
        }
        if (t.getMontant() == null || t.getMontant().compareTo(BigDecimal.ZERO) <= 0) {
            return "Montant négatif ou nul";
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
                .orElseThrow(() -> new IllegalArgumentException("XSD introuvable : " + id));
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
