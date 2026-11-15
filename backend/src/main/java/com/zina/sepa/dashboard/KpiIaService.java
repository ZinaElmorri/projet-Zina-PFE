package com.zina.sepa.dashboard;

import com.zina.sepa.domain.entity.Transaction;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.service.BusinessControlService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Indicateurs de l'apport de l'IA sur une période (agrégats, aucune donnée de salarié).
 *
 * <p>« Détectée par l'IA seule » : fiche signalée par l'IA que les contrôles métier ne signalent pas (IBAN ou BIC
 * invalide, montant nul ou négatif, doublon IBAN + montant dans la période). Les fiches comptées sont celles de
 * la dernière analyse IA de chaque fiche.</p>
 */
@Service
public class KpiIaService {

    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    public record KpiIa(String periode, long anomaliesIa, long anomaliesIaSeule, BigDecimal partIaSeule,
                        long anomaliesIaAussiVuesParRegles) {}

    private final KpiIaRepository repository;
    private final BusinessControlService controles;

    public KpiIaService(KpiIaRepository repository, BusinessControlService controles) {
        this.repository = repository;
        this.controles = controles;
    }

    public KpiIa kpi(String periodeDemandee, String clientIdDemande) {
        YearMonth mois = periode(periodeDemandee);
        String clientId = clientIdDemande == null || clientIdDemande.isBlank() ? null : clientIdDemande.trim();
        LocalDateTime debut = mois.atDay(1).atStartOfDay();
        LocalDateTime fin = mois.plusMonths(1).atDay(1).atStartOfDay();

        List<Transaction> suspects = repository.suspectsIa(TransactionStatus.SUPPRIMEE, debut, fin, clientId);
        long iaSeule = suspects.stream().filter(t -> !vueParLesRegles(t, debut, fin, clientId)).count();
        long total = suspects.size();
        BigDecimal part = total == 0 ? null : BigDecimal.valueOf(iaSeule * 100.0 / total).setScale(1, RoundingMode.HALF_UP);
        return new KpiIa(mois.format(YM), total, iaSeule, part, total - iaSeule);
    }

    private boolean vueParLesRegles(Transaction t, LocalDateTime debut, LocalDateTime fin, String clientId) {
        if (t.getIban() == null || t.getIban().isBlank() || !controles.ibanValide(t.getIban())) {
            return true;
        }
        if (t.getBic() != null && !t.getBic().isBlank() && !controles.bicValide(t.getBic())) {
            return true;
        }
        if (t.getMontant() == null || t.getMontant().compareTo(BigDecimal.ZERO) <= 0) {
            return true;
        }
        return repository.countIdentiques(t.getIban(), t.getMontant(), TransactionStatus.SUPPRIMEE, debut, fin, clientId) > 1;
    }

    private static YearMonth periode(String periode) {
        if (periode == null || periode.isBlank()) {
            return YearMonth.now().minusMonths(1);
        }
        try {
            return YearMonth.parse(periode);
        } catch (Exception e) {
            return YearMonth.now().minusMonths(1);
        }
    }
}
