package com.zina.sepa.dashboard;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Indicateurs « IA » du tableau de bord KPI, en lecture seule. */
@RestController
@RequestMapping("/api/dashboard/kpi/ia")
public class KpiIaController {

    private final KpiIaService service;

    public KpiIaController(KpiIaService service) {
        this.service = service;
    }

    @GetMapping
    public KpiIaService.KpiIa kpi(@RequestParam(required = false) String periode,
                                  @RequestParam(required = false) String clientId) {
        return service.kpi(periode, clientId);
    }
}
