package com.zina.sepa.masse;

import com.zina.sepa.masse.dto.MasseDto.*;
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
@PreAuthorize("hasAnyRole('ADMINISTRATEUR','GESTIONNAIRE_RH','VALIDATEUR')")
public class MassController {

    private final MassPaymentService service;

    public MassController(MassPaymentService service) {
        this.service = service;
    }

    @GetMapping("/filtres")
    public FiltresResponse filtres(@RequestParam(required = false) String clientId,
                                   @RequestParam(required = false) String periode) {
        return service.filtres(clientId, periode);
    }

    @GetMapping("/paiements")
    public PaiementsResponse paiements(@RequestParam(required = false) String clientId,
                                       @RequestParam(required = false) String periode,
                                       @RequestParam(required = false) List<String> matricules,
                                       @RequestParam(required = false) List<String> statuts) {
        return service.paiements(clientId, periode, matricules, statuts);
    }

    @GetMapping("/xsd")
    public List<XsdUtilisable> xsd() {
        return service.xsdUtilisables();
    }

    @GetMapping("/xsd/{id}/analyse")
    public AnalyseXsd analyse(@PathVariable Long id) {
        return service.analyse(id);
    }

    @PostMapping("/apercu")
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','GESTIONNAIRE_RH')")
    public ApercuResponse apercu(@RequestBody ApercuRequest req, HttpServletRequest http) {
        return service.apercu(req, http);
    }

    @PostMapping("/fichiers")
    @PreAuthorize("hasAnyRole('ADMINISTRATEUR','GESTIONNAIRE_RH')")
    public FichierResume enregistrer(@RequestBody ApercuRequest req, HttpServletRequest http) {
        FichierSepaGenere f = service.enregistrer(req, http);
        return service.detail(f.getId()).resume();
    }

    @GetMapping("/fichiers")
    public List<FichierResume> historique(@RequestParam(required = false) String clientId,
                                          @RequestParam(required = false) String periode,
                                          @RequestParam(required = false) Boolean valideXsd) {
        return service.historique(clientId, periode, valideXsd);
    }

    @GetMapping("/fichiers/{id}")
    public FichierDetail detail(@PathVariable Long id) {
        return service.detail(id);
    }

    @GetMapping("/fichiers/{id}/telecharger")
    public ResponseEntity<byte[]> telecharger(@PathVariable Long id, HttpServletRequest http) {
        FichierSepaGenere f = service.pourTelecharger(id, http);
        byte[] contenu = f.getContenuXml().getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.getNomFichier() + "\"")
                .contentType(MediaType.APPLICATION_XML)
                .body(contenu);
    }
}
