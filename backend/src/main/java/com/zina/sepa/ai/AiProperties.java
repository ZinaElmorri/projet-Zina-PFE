package com.zina.sepa.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Paramètres du module IA de détection des montants suspects.
 * Préfixe {@code app.ai} (distinct de {@code app.ai-module} historique).
 */
@ConfigurationProperties(prefix = "app.ai")
public class AiProperties {

    /** Interrupteur global : si false, aucun appel IA n'est effectué. */
    private boolean enabled = false;

    /** URL de base du service IA (FastAPI). */
    private String url = "http://localhost:8000";

    /** Timeout de connexion et de lecture (ms). */
    private int timeoutMs = 5000;

    /** Seuil de score au-delà duquel un paiement est considéré suspect. */
    private double scoreThreshold = 0.6;

    /** Nombre de mois d'historique transmis au modèle. */
    private int historyMonths = 6;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public double getScoreThreshold() {
        return scoreThreshold;
    }

    public void setScoreThreshold(double scoreThreshold) {
        this.scoreThreshold = scoreThreshold;
    }

    public int getHistoryMonths() {
        return historyMonths;
    }

    public void setHistoryMonths(int historyMonths) {
        this.historyMonths = historyMonths;
    }
}
