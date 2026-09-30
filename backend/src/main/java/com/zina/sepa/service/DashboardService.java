package com.zina.sepa.service;

import com.zina.sepa.domain.enums.RiskLevel;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.dto.DashboardStats;
import com.zina.sepa.repository.TransactionRepository;
import com.zina.sepa.repository.XsdSchemaRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final XsdSchemaRepository xsdSchemaRepository;

    public DashboardService(TransactionRepository transactionRepository,
                            XsdSchemaRepository xsdSchemaRepository) {
        this.transactionRepository = transactionRepository;
        this.xsdSchemaRepository = xsdSchemaRepository;
    }

    public DashboardStats stats() {
        long total = transactionRepository.count();
        long approuvees = transactionRepository.countByStatut(TransactionStatus.APPROUVEE);
        long anomalies = transactionRepository.findAll().stream()
                .filter(t -> t.getNiveauRisque() == RiskLevel.ELEVE
                        || t.getNiveauRisque() == RiskLevel.CRITIQUE)
                .count();
        long schemas = xsdSchemaRepository.count();

        return new DashboardStats(total, approuvees, anomalies, approuvees, 0, schemas);
    }
}
