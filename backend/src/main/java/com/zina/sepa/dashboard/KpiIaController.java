package com.zina.sepa.dashboard;

import com.zina.sepa.masse.AccesSocietes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Indicateurs « IA » du tableau de bord KPI, en lecture seule (un Expert RH ne voit que ses sociétés). */
@RestController
@RequestMapping("/api/dashboard/kpi/ia")
public class KpiIaController {

    private final KpiIaService service;
    private final AccesSocietes acces;

    public KpiIaController(KpiIaService service, AccesSocietes acces) {
        this.service = service;
        this.acces = acces;
    }

    @GetMapping
    public KpiIaService.KpiIa kpi(@RequestParam(required = false) String periode,
                                  @RequestParam(required = false) String clientId) {
        String societe = clientId == null || clientId.isBlank() ? null : clientId.trim();
        var perimetre = acces.perimetre();
        if (perimetre.isEmpty()) {
            return service.kpi(periode, societe);
        }
        if (societe != null) {
            acces.verifier(societe);
            return service.kpi(periode, societe);
        }
        return service.kpiPourSocietes(periode, perimetre.get());
    }
}
