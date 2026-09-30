"""
Moteur de détection d'anomalies sur les virements SEPA.

Combine :
- Isolation Forest (anomalies globales sur le montant)
- Local Outlier Factor (densité locale)
- Règles statistiques (z-score) sur les montants
- Détection de doublons (IBAN + montant)
- Contrôle d'IBAN atypiques

Chaque transaction reçoit un score de risque dans [0, 1] et un niveau.
"""
from typing import List

import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.neighbors import LocalOutlierFactor

from .schemas import AnomalyOut, TransactionIn


def _niveau(score: float) -> str:
    if score >= 0.85:
        return "CRITIQUE"
    if score >= 0.6:
        return "ELEVE"
    if score >= 0.35:
        return "MOYEN"
    return "FAIBLE"


def detecter(transactions: List[TransactionIn]) -> List[AnomalyOut]:
    if not transactions:
        return []

    df = pd.DataFrame([t.model_dump() for t in transactions])
    n = len(df)
    montants = df["montant"].to_numpy(dtype=float).reshape(-1, 1)

    # --- Score statistique (z-score sur le montant) ---
    mean = float(df["montant"].mean())
    std = float(df["montant"].std(ddof=0)) or 1.0
    zscores = np.abs((df["montant"].to_numpy(dtype=float) - mean) / std)
    score_stat = np.clip(zscores / 3.0, 0.0, 1.0)

    # --- Isolation Forest ---
    if n >= 4:
        iso = IsolationForest(contamination="auto", random_state=42)
        iso.fit(montants)
        # decision_function : plus c'est bas, plus c'est anormal
        raw = -iso.decision_function(montants)
        score_iso = (raw - raw.min()) / (np.ptp(raw) or 1.0)
    else:
        score_iso = np.zeros(n)

    # --- Local Outlier Factor ---
    if n >= 5:
        lof = LocalOutlierFactor(n_neighbors=min(20, n - 1))
        lof.fit_predict(montants)
        lof_scores = -lof.negative_outlier_factor_
        score_lof = (lof_scores - lof_scores.min()) / (np.ptp(lof_scores) or 1.0)
    else:
        score_lof = np.zeros(n)

    # --- Doublons (même IBAN + montant) ---
    doublon = df.duplicated(subset=["iban", "montant"], keep=False).to_numpy()

    # --- IBAN atypique (longueur inhabituelle) ---
    iban_len = df["iban"].fillna("").str.replace(r"\s", "", regex=True).str.len()
    iban_atypique = ((iban_len < 15) | (iban_len > 34)).to_numpy()

    resultats: List[AnomalyOut] = []
    for i in range(n):
        score = float(np.average(
            [score_stat[i], score_iso[i], score_lof[i]],
            weights=[0.4, 0.35, 0.25],
        ))
        raisons = []
        if score_stat[i] > 0.6:
            raisons.append("montant statistiquement anormal")
        if doublon[i]:
            score = max(score, 0.7)
            raisons.append("doublon potentiel")
        if iban_atypique[i]:
            score = max(score, 0.6)
            raisons.append("IBAN atypique")

        score = round(min(score, 1.0), 3)
        resultats.append(AnomalyOut(
            transactionId=int(df.iloc[i]["transactionId"]),
            scoreRisque=score,
            niveauRisque=_niveau(score),
            explication="; ".join(raisons) if raisons else "aucune anomalie détectée",
        ))
    return resultats
