package com.zina.sepa.affectation;

import com.zina.sepa.affectation.AffectationService.AffectationDto;
import com.zina.sepa.affectation.AffectationService.ExpertDto;
import com.zina.sepa.affectation.AffectationService.HistoriqueDto;
import com.zina.sepa.security.Roles;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/affectations")
@PreAuthorize(Roles.AFFECTATION)
public class AffectationController {

    public record AffectationRequest(Long expertId) {}

    private final AffectationService service;

    public AffectationController(AffectationService service) {
        this.service = service;
    }

    @GetMapping
    public List<AffectationDto> lister() {
        return service.lister();
    }

    @GetMapping("/experts")
    public List<ExpertDto> experts() {
        return service.experts();
    }

    @GetMapping("/historique")
    public List<HistoriqueDto> historique(@RequestParam String societe) {
        return service.historique(societe);
    }

    @PutMapping("/{societeCode}")
    public AffectationDto affecter(@PathVariable String societeCode, @RequestBody AffectationRequest req,
                                   HttpServletRequest http) {
        if (req.expertId() == null) {
            throw new IllegalArgumentException("Choisissez l'Expert RH à qui affecter la société.");
        }
        return service.affecter(societeCode, req.expertId(), http);
    }

    @DeleteMapping("/{societeCode}")
    public ResponseEntity<Void> retirer(@PathVariable String societeCode, HttpServletRequest http) {
        service.retirer(societeCode, http);
        return ResponseEntity.noContent().build();
    }
}
