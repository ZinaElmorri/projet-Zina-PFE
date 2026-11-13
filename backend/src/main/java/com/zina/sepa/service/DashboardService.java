package com.zina.sepa.service;

import com.zina.sepa.domain.enums.RiskLevel;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.dto.DashboardStats;
import com.zina.sepa.masse.FichierSepaGenereRepository;
import com.zina.sepa.repository.TransactionRepository;
import com.zina.sepa.repository.XsdSchemaRepository;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final TransactionRepository transactionRepository;
    private final XsdSchemaRepository xsdSchemaRepository;
    private final FichierSepaGenereRepository fichierRepository;

    public DashboardService(TransactionRepository transactionRepository,
                            XsdSchemaRepository xsdSchemaRepository,
                            FichierSepaGenereRepository fichierRepository) {
        this.transactionRepository = transactionRepository;
        this.xsdSchemaRepository = xsdSchemaRepository;
        this.fichierRepository = fichierRepository;
    }

    public DashboardStats stats() {
        long total = transactionRepository.count();
        long approuvees = transactionRepository.countByStatut(TransactionStatus.APPROUVEE);
        long anomalies = transactionRepository.findAll().stream()
                .filter(t -> t.getNiveauRisque() == RiskLevel.ELEVE
                        || t.getNiveauRisque() == RiskLevel.CRITIQUE)
                .count();
        long schemas = xsdSchemaRepository.count();
        // Fichiers réellement générés (historique) et ceux dont le XML n'est pas conforme au XSD.
        long fichiers = fichierRepository.count();
        long fichiersNonConformes = fichierRepository.countByValideXsd(false);

        return new DashboardStats(total, approuvees, anomalies, fichiers, fichiersNonConformes, schemas);
    }
}
