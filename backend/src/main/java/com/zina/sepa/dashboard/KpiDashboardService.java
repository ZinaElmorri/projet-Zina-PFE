package com.zina.sepa.dashboard;

import com.zina.sepa.dashboard.dto.KpiResponse;
import com.zina.sepa.dashboard.dto.KpiResponse.EvolutionPoint;
import com.zina.sepa.dashboard.dto.KpiResponse.LabelValue;
import com.zina.sepa.dashboard.dto.KpiResponse.StatutCount;
import com.zina.sepa.dashboard.dto.KpiResponse.TopClient;
import com.zina.sepa.domain.enums.TransactionStatus;
import com.zina.sepa.recommendation.AnomalieStatut;
import com.zina.sepa.recommendation.AnomalieType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Calcul des KPI en lecture seule (agrégations SQL uniquement).
 *
 * <p><b>Définition d'une « erreur bloquante »</b> : anomalie dont le type empêche la
 * génération SEPA (IBAN/BIC invalide, montant invalide, doublon, XSD, somme de contrôle…)
 * et dont le statut n'est pas IGNOREE. Les avertissements (MONTANT_SUSPECT de l'IA,
 * IBAN_PARTAGE) ne sont pas comptés comme erreurs.</p>
 *
 * <p><b>« Fiche générée »</b> : transaction au statut APPROUVEE sur la période (ce sont
 * les fiches consommées par la génération pain.001). Aucun enregistrement de lot/fichier
 * n'étant persisté, {@code lotsGeneres}/{@code lotsValidesXsd} sont indisponibles (null).</p>
 */
@Service
public class KpiDashboardService {

    private static final Logger log = LoggerFactory.getLogger(KpiDashboardService.class);
    private static final DateTimeFormatter YM = DateTimeFormatter.ofPattern("yyyy-MM");

    /** Types considérés comme des avertissements (non bloquants). */
    private static final Set<String> AVERTISSEMENTS = Set.of(
            AnomalieType.MONTANT_SUSPECT, AnomalieType.IBAN_PARTAGE);

    /** Types d'erreurs bloquantes (tous sauf les avertissements). */
    private static final List<String> TYPES_BLOQUANTS = List.of(
            AnomalieType.IBAN_INVALIDE, AnomalieType.BIC_INVALIDE, AnomalieType.MONTANT_INVALIDE,
            AnomalieType.DOUBLON_PAIEMENT, AnomalieType.NET_SUPERIEUR_BRUT,
            AnomalieType.ERREUR_XSD, AnomalieType.CTRL_SUM_INCOHERENT);

    private final DashboardTransactionRepository transactionRepository;
    private final DashboardAnomalieRepository anomalieRepository;

    public KpiDashboardService(DashboardTransactionRepository transactionRepository,
                               DashboardAnomalieRepository anomalieRepository) {
        this.transactionRepository = transactionRepository;
        this.anomalieRepository = anomalieRepository;
    }

    public KpiResponse kpi(DashboardFilter filtre) {
        YearMonth mois = periode(filtre == null ? null : filtre.periode());
        String periode = mois.format(YM);
        String clientId = filtre == null ? null : vide(filtre.clientId());
        LocalDateTime debut = mois.atDay(1).atStartOfDay();
        LocalDateTime fin = mois.plusMonths(1).atDay(1).atStartOfDay();

        long fiches = safe(() -> transactionRepository.countFiches(
                TransactionStatus.APPROUVEE, debut, fin, clientId), 0L);
        long clients = safe(() -> transactionRepository.countClients(
                TransactionStatus.APPROUVEE, debut, fin, clientId), 0L);
        long nbErreurs = safe(() -> anomalieRepository.countErreursBloquantes(
                periode, TYPES_BLOQUANTS, AnomalieStatut.IGNOREE, clientId), 0L);
        BigDecimal montant = safe(() -> transactionRepository.sumMontant(
                TransactionStatus.APPROUVEE, debut, fin, clientId), BigDecimal.ZERO);

        return new KpiResponse(
                periode,
                fiches,
                clients,
                nbErreurs,
                taux(nbErreurs, fiches),
                montant,
                null, // lotsGeneres : indisponible (aucune persistance de lot)
                null, // lotsValidesXsd : indisponible
                safe(() -> parType(periode, clientId), List.of()),
                safe(() -> parSource(periode, clientId), List.of()),
                safe(() -> parStatut(periode, clientId), new StatutCount(0, 0, 0)),
                List.of(), // clientsParDepartement : notion inexistante
                List.of(), // clientsParExpert : notion inexistante
                safe(() -> evolution(mois, clientId), List.of()),
                safe(() -> topClients(periode, clientId), List.of()));
    }

    // ------------------------------------------------------------------

    private List<LabelValue> parType(String periode, String clientId) {
        return anomalieRepository.anomaliesParType(periode, clientId).stream()
                .map(r -> new LabelValue((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    private List<LabelValue> parSource(String periode, String clientId) {
        Map<String, Long> parSource = new LinkedHashMap<>();
        for (Object[] r : anomalieRepository.anomaliesParType(periode, clientId)) {
            String source = source((String) r[0]);
            parSource.merge(source, ((Number) r[1]).longValue(), Long::sum);
        }
        return parSource.entrySet().stream()
                .map(e -> new LabelValue(e.getKey(), e.getValue()))
                .toList();
    }

    private StatutCount parStatut(String periode, String clientId) {
        long ouvertes = 0, corrigees = 0, ignorees = 0;
        for (Object[] r : anomalieRepository.anomaliesParStatut(periode, clientId)) {
            AnomalieStatut statut = (AnomalieStatut) r[0];
            long n = ((Number) r[1]).longValue();
            switch (statut) {
                case OUVERTE -> ouvertes = n;
                case CORRIGEE -> corrigees = n;
                case IGNOREE -> ignorees = n;
            }
        }
        return new StatutCount(ouvertes, corrigees, ignorees);
    }

    private List<TopClient> topClients(String periode, String clientId) {
        return anomalieRepository.topClients(periode, AnomalieStatut.IGNOREE, clientId, PageRequest.of(0, 5))
                .stream()
                .map(r -> new TopClient((String) r[0], ((Number) r[1]).longValue()))
                .toList();
    }

    private List<EvolutionPoint> evolution(YearMonth mois, String clientId) {
        List<YearMonth> douzeMois = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            douzeMois.add(mois.minusMonths(i));
        }
        LocalDateTime debut = douzeMois.get(0).atDay(1).atStartOfDay();
        LocalDateTime fin = mois.plusMonths(1).atDay(1).atStartOfDay();

        Map<String, Long> fichesParMois = new LinkedHashMap<>();
        for (Object[] r : transactionRepository.countFichesParMois(
                TransactionStatus.APPROUVEE, debut, fin, clientId)) {
            String cle = String.format("%04d-%02d", ((Number) r[0]).intValue(), ((Number) r[1]).intValue());
            fichesParMois.put(cle, ((Number) r[2]).longValue());
        }

        List<String> periodes = douzeMois.stream().map(m -> m.format(YM)).toList();
        Map<String, Long> erreursParMois = new LinkedHashMap<>();
        for (Object[] r : anomalieRepository.erreursParPeriodes(
                periodes, TYPES_BLOQUANTS, AnomalieStatut.IGNOREE, clientId)) {
            erreursParMois.put((String) r[0], ((Number) r[1]).longValue());
        }

        List<EvolutionPoint> points = new ArrayList<>();
        for (String p : periodes) {
            long f = fichesParMois.getOrDefault(p, 0L);
            long e = erreursParMois.getOrDefault(p, 0L);
            points.add(new EvolutionPoint(p, f, e, taux(e, f)));
        }
        return points;
    }

    private String source(String type) {
        if (AnomalieType.MONTANT_SUSPECT.equals(type)) {
            return "IA";
        }
        if (AnomalieType.ERREUR_XSD.equals(type) || AnomalieType.CTRL_SUM_INCOHERENT.equals(type)) {
            return "XSD";
        }
        return "REGLE";
    }

    private BigDecimal taux(long erreurs, long fiches) {
        if (fiches <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(erreurs)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(fiches), 2, RoundingMode.HALF_UP);
    }

    private YearMonth periode(String periode) {
        if (periode == null || periode.isBlank()) {
            return YearMonth.now().minusMonths(1);
        }
        try {
            return YearMonth.parse(periode);
        } catch (Exception e) {
            return YearMonth.now().minusMonths(1);
        }
    }

    private String vide(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Exécute un calcul d'indicateur ; en cas d'échec, renvoie la valeur de repli. */
    private <T> T safe(Supplier<T> calcul, T repli) {
        try {
            return calcul.get();
        } catch (Exception e) {
            log.warn("Indicateur KPI indisponible : {}", e.getMessage());
            return repli;
        }
    }
}
