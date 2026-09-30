package com.zina.sepa.dashboard;

import com.zina.sepa.dashboard.dto.KpiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint KPI en lecture seule. Filtres optionnels : periode, clientId,
 * departementId, expertId (ces deux derniers acceptés mais ignorés pour l'instant).
 */
@RestController
@RequestMapping("/api/dashboard/kpi")
public class KpiDashboardController {

    private final KpiDashboardService service;

    public KpiDashboardController(KpiDashboardService service) {
        this.service = service;
    }

    @GetMapping
    public KpiResponse kpi(@RequestParam(required = false) String periode,
                           @RequestParam(required = false) String clientId,
                           @RequestParam(required = false) String departementId,
                           @RequestParam(required = false) String expertId) {
        return service.kpi(new DashboardFilter(periode, clientId, departementId, expertId));
    }
}
