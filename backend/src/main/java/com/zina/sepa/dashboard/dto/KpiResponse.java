package com.zina.sepa.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

/** Réponse du dashboard KPI (lecture seule). */
public record KpiResponse(
        String periode,
        long fichesGenerees,
        long clientsConcernes,
        long nbErreurs,
        BigDecimal tauxErreur,
        BigDecimal montantTotalVire,
        Long lotsGeneres,
        Long lotsValidesXsd,
        List<LabelValue> anomaliesParType,
        List<LabelValue> anomaliesParSource,
        StatutCount anomaliesParStatut,
        List<LabelValue> clientsParDepartement,
        List<LabelValue> clientsParExpert,
        List<EvolutionPoint> evolutionMensuelle,
        List<TopClient> topClientsAnomalies
) {
    public record LabelValue(String label, long value) {}

    public record StatutCount(long ouvertes, long corrigees, long ignorees) {}

    public record EvolutionPoint(String periode, long fichesGenerees, long nbErreurs, BigDecimal tauxErreur) {}

    public record TopClient(String clientId, long nbAnomalies) {}
}
