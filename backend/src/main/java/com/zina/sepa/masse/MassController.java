package com.zina.sepa.masse;

import com.zina.sepa.masse.dto.MasseDto.*;
import com.zina.sepa.security.Roles;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Génération de virements de masse SEPA (pain.001) : filtres, analyse XSD, aperçu+validation,
 * enregistrement, historique et téléchargement. Package isolé, n'impacte pas l'existant.
 */
@RestController
@RequestMapping("/api/masse")
@PreAuthorize(Roles.CONSULTATION)
public class MassController {

    public record RejetRequest(String motif) {}

    private final MassPaymentService service;
    private final FichierWorkflowService workflow;

    public MassController(MassPaymentService service, FichierWorkflowService workflow) {
        this.service = service;
        this.workflow = workflow;
    }

    @GetMapping("/filtres")
    @PreAuthorize(Roles.METIER)
    public FiltresResponse filtres(@RequestParam(required = false) String clientId,
                                   @RequestParam(required = false) String periode) {
        return service.filtres(clientId, periode);
    }

    @GetMapping("/paiements")
    @PreAuthorize(Roles.METIER)
    public PaiementsResponse paiements(@RequestParam(required = false) String clientId,
                                       @RequestParam(required = false) String periode,
                                       @RequestParam(required = false) List<String> matricules,
                                       @RequestParam(required = false) List<String> statuts) {
        return service.paiements(clientId, periode, matricules, statuts);
    }

    @PostMapping("/analyse-ia")
    @PreAuthorize(Roles.METIER)
    public AnalyseIaMasse analyseIa(@RequestParam(required = false) String clientId,
                                    @RequestParam(required = false) String periode,
                                    @RequestParam(required = false) List<String> matricules,
                                    @RequestParam(required = false) List<String> statuts) {
        return service.analyserIa(clientId, periode, matricules, statuts);
    }

    @GetMapping("/xsd")
    @PreAuthorize(Roles.METIER)
    public List<XsdUtilisable> xsd() {
        return service.xsdUtilisables();
    }

    @GetMapping("/xsd/{id}/analyse")
    @PreAuthorize(Roles.METIER)
    public AnalyseXsd analyse(@PathVariable Long id) {
        return service.analyse(id);
    }

    @PostMapping("/apercu")
    @PreAuthorize(Roles.PREPARATION)
    public ApercuResponse apercu(@RequestBody ApercuRequest req, HttpServletRequest http) {
        return service.apercu(req, http);
    }

    @PostMapping("/fichiers")
    @PreAuthorize(Roles.PREPARATION)
    public FichierResume enregistrer(@RequestBody ApercuRequest req, HttpServletRequest http) {
        FichierSepaGenere f = service.enregistrer(req, http);
        return service.detail(f.getId()).resume();
    }

    @PostMapping("/controle")
    @PreAuthorize(Roles.PREPARATION)
    public FichierResume controler(@RequestBody ApercuRequest req, HttpServletRequest http) {
        FichierSepaGenere f = service.controler(req, http);
        return service.detail(f.getId()).resume();
    }

    @PostMapping("/fichiers/{id}/soumettre")
    @PreAuthorize(Roles.PREPARATION)
    public FichierResume soumettre(@PathVariable Long id, HttpServletRequest http) {
        return workflow.soumettre(id, http);
    }

    @PostMapping("/fichiers/{id}/valider")
    @PreAuthorize(Roles.VALIDATION)
    public FichierResume valider(@PathVariable Long id, HttpServletRequest http) {
        return workflow.valider(id, http);
    }

    @PostMapping("/fichiers/{id}/rejeter")
    @PreAuthorize(Roles.VALIDATION)
    public FichierResume rejeter(@PathVariable Long id, @RequestBody RejetRequest req, HttpServletRequest http) {
        return workflow.rejeter(id, req.motif(), http);
    }

    @GetMapping("/fichiers")
    public List<FichierResume> historique(@RequestParam(required = false) String clientId,
                                          @RequestParam(required = false) String periode,
                                          @RequestParam(required = false) Boolean valideXsd,
                                          @RequestParam(required = false) String statut) {
        return service.historique(clientId, periode, valideXsd, statut);
    }

    @GetMapping("/fichiers/{id}")
    @PreAuthorize(Roles.METIER)
    public FichierDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/fichiers/{id}/resultat")
    @PreAuthorize(Roles.METIER)
    public ResultatFichier resultat(@PathVariable Long id) {
        return service.resultat(id);
    }

    @GetMapping("/fichiers/{id}/telecharger")
    @PreAuthorize(Roles.METIER)
    public ResponseEntity<byte[]> telecharger(@PathVariable Long id, HttpServletRequest http) {
        FichierSepaGenere f = service.pourTelecharger(id, http);
        byte[] contenu = f.getContenuXml().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + service.nomTelechargement(f) + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(contenu);
    }
}
