package com.zina.sepa.dto;

public record DashboardStats(
        long totalTransactions,
        long transactionsApprouvees,
        long anomaliesDetectees,
        long fichiersGeneres,
        long erreursXsd,
        long schemasXsd
) {}
