"""Calcul des features comportementales pour chaque paiement d'un lot."""
from typing import Dict, List

import numpy as np
import pandas as pd

from .config import FEATURES, HISTORY_MONTHS


def compute_features(paiements: List[Dict]) -> pd.DataFrame:
    """Transforme une liste de paiements en DataFrame de features.

    Colonnes produites : FEATURES + colonnes techniques (ficheId).
    """
    rows = []
    for p in paiements:
        historique = [float(x) for x in (p.get("historique") or [])][-HISTORY_MONTHS:]
        montant = float(p["montant"])

        if historique:
            moyenne = float(np.mean(historique))
            ecart = montant / moyenne if moyenne else 1.0
            precedent = float(historique[-1])
            variation = ((montant - precedent) / precedent * 100.0) if precedent else 0.0
        else:
            # Pas d'historique : valeurs neutres.
            ecart = 1.0
            variation = 0.0

        rows.append({
            "ficheId": int(p["ficheId"]),
            "clientId": str(p.get("clientId")) if p.get("clientId") is not None else "_",
            "iban": (p.get("iban") or "").replace(" ", "").upper(),
            "montant": montant,
            "ecart_moyenne_salarie": ecart,
            "variation_mois_precedent": variation,
            "iban_change_recent": 1 if p.get("ibanChange") else 0,
        })

    df = pd.DataFrame(rows)

    # z-score du montant par rapport aux autres salariés du même client.
    df["zscore_client"] = 0.0
    for _, groupe in df.groupby("clientId"):
        moyenne = groupe["montant"].mean()
        ecart_type = groupe["montant"].std(ddof=0) or 1.0
        df.loc[groupe.index, "zscore_client"] = (groupe["montant"] - moyenne) / ecart_type

    # Nombre de paiements partageant le même IBAN dans le lot.
    df["nb_paiements_meme_iban"] = df.groupby("iban")["ficheId"].transform("count")

    # Garantit la présence et l'ordre des colonnes du modèle.
    for col in FEATURES:
        if col not in df.columns:
            df[col] = 0.0
    return df


def build_reasons(row: pd.Series) -> List[str]:
    """Construit une explication lisible pour un paiement signalé."""
    raisons: List[str] = []

    ecart = float(row["ecart_moyenne_salarie"])
    if ecart >= 1.5:
        raisons.append(f"Montant {ecart:.1f}\u00d7 la moyenne des 6 derniers mois".replace(".", ","))
    if int(row["iban_change_recent"]) == 1:
        raisons.append("IBAN modifi\u00e9 ce mois-ci")
    if abs(float(row["zscore_client"])) >= 2.0:
        raisons.append("Montant tr\u00e8s \u00e9loign\u00e9 des autres salari\u00e9s du client")
    variation = float(row["variation_mois_precedent"])
    if variation >= 50.0:
        raisons.append(f"Hausse de {variation:.0f}% par rapport au mois pr\u00e9c\u00e9dent")
    if int(row["nb_paiements_meme_iban"]) > 1:
        raisons.append(f"IBAN partag\u00e9 par {int(row['nb_paiements_meme_iban'])} paiements du lot")

    if not raisons:
        raisons.append("Profil de paiement inhabituel d\u00e9tect\u00e9 par le mod\u00e8le")
    return raisons
