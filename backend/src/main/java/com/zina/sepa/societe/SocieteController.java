package com.zina.sepa.societe;

import com.zina.sepa.security.Roles;
import com.zina.sepa.societe.SocieteService.Coordonnees;
import com.zina.sepa.societe.SocieteService.SocieteDto;
import com.zina.sepa.societe.SocieteService.SocieteRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/societes")
public class SocieteController {

    private final SocieteService service;

    public SocieteController(SocieteService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(Roles.CONSULTATION)
    public List<SocieteDto> lister() {
        return service.lister();
    }

    @GetMapping("/{code}/coordonnees")
    @PreAuthorize(Roles.PREPARATION)
    public Coordonnees coordonnees(@PathVariable String code) {
        return service.coordonnees(code);
    }

    @PostMapping
    @PreAuthorize(Roles.ADMIN)
    public SocieteDto creer(@RequestBody SocieteRequest req) {
        return service.creer(req);
    }

    @PutMapping("/{id}")
    @PreAuthorize(Roles.ADMIN)
    public SocieteDto modifier(@PathVariable Long id, @RequestBody SocieteRequest req) {
        return service.modifier(id, req);
    }
}
