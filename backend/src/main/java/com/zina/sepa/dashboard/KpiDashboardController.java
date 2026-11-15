package com.zina.sepa.dashboard;

import com.zina.sepa.dashboard.dto.KpiResponse;
import com.zina.sepa.masse.AccesSocietes;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint KPI en lecture seule. Filtres optionnels : periode, clientId,
 * departementId, expertId (ces deux derniers acceptés mais ignorés pour l'instant).
 * Un Expert RH ne voit que les sociétés qui lui sont affectées.
 */
@RestController
@RequestMapping("/api/dashboard/kpi")
public class KpiDashboardController {

    private final KpiDashboardService service;
    private final AccesSocietes acces;

    public KpiDashboardController(KpiDashboardService service, AccesSocietes acces) {
        this.service = service;
        this.acces = acces;
    }

    @GetMapping
    public KpiResponse kpi(@RequestParam(required = false) String periode,
                           @RequestParam(required = false) String clientId,
                           @RequestParam(required = false) String departementId,
                           @RequestParam(required = false) String expertId) {
        String societe = clientId == null || clientId.isBlank() ? null : clientId.trim();
        var perimetre = acces.perimetre();
        if (perimetre.isEmpty()) {
            return service.kpi(new DashboardFilter(periode, societe, departementId, expertId));
        }
        if (societe != null) {
            acces.verifier(societe);
            return service.kpi(new DashboardFilter(periode, societe, departementId, expertId));
        }
        return service.kpiPourSocietes(new DashboardFilter(periode, null, departementId, expertId), perimetre.get());
    }
}
