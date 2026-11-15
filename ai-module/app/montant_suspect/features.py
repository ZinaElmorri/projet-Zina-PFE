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
            "salarie": str(p.get("salarieId")) if p.get("salarieId") not in (None, "") else f"_{int(p['ficheId'])}",
            "iban": (p.get("iban") or "").replace(" ", "").upper(),
            "montant": montant,
            "ecart_moyenne_salarie": ecart,
            "variation_mois_precedent": variation,
            "iban_change_recent": 1 if p.get("ibanChange") else 0,
            "sans_historique": 0 if historique else 1,
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
    # Fiches strictement identiques (même salarié, IBAN et montant) : explication « doublon », hors modèle.
    df["nb_fiches_identiques"] = df.groupby(["salarie", "iban", "montant"])["ficheId"].transform("count")
    # Salariés distincts partageant le même IBAN dans le lot (explication « IBAN partagé »), hors modèle.
    df["nb_salaries_meme_iban"] = df.groupby("iban")["salarie"].transform("nunique")

    # Garantit la présence et l'ordre des colonnes du modèle.
    for col in FEATURES:
        if col not in df.columns:
            df[col] = 0.0
    return df


def build_reasons(row: pd.Series) -> List[str]:
    """Construit une explication lisible pour un paiement signalé."""
    raisons: List[str] = []

    montant = float(row["montant"])
    if montant <= 0:
        raisons.append("Montant nul ou n\u00e9gatif")
    if int(row.get("nb_fiches_identiques", 1)) > 1:
        raisons.append(f"Fiche identique pr\u00e9sente {int(row['nb_fiches_identiques'])} fois dans le lot "
                       "(m\u00eame salari\u00e9, m\u00eame IBAN, m\u00eame montant)")
    sans_historique = int(row.get("sans_historique", 0)) == 1
    if sans_historique:
        raisons.append("Aucun historique de paie pour ce salari\u00e9")

    ecart = float(row["ecart_moyenne_salarie"])
    if ecart >= 1.25:
        raisons.append(f"Montant {ecart:.1f}\u00d7 la moyenne des 6 derniers mois".replace(".", ","))
    elif montant > 0 and not sans_historique and ecart <= 0.6:
        raisons.append(f"Montant \u00e9gal \u00e0 {ecart * 100:.0f}\u202f% de la moyenne des 6 derniers mois")
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


# Type d'anomalie probable et recommandation de correction, déduits des mêmes variables que les raisons.
RECOMMANDATIONS = {
    "Montant nul ou n\u00e9gatif": "Corriger le montant dans la fiche de paie : un virement doit \u00eatre strictement positif.",
    "Doublon probable": "V\u00e9rifier si le salari\u00e9 figure deux fois dans le fichier et supprimer la fiche en double.",
    "IBAN modifi\u00e9 et montant \u00e9lev\u00e9": "Demander au salari\u00e9 un justificatif du nouvel IBAN (RIB sign\u00e9) avant tout virement, puis confirmer le montant.",
    "IBAN modifi\u00e9": "Demander au salari\u00e9 un justificatif du nouvel IBAN (RIB sign\u00e9) avant le virement.",
    "Salari\u00e9 sans historique": "V\u00e9rifier l'existence et l'embauche du salari\u00e9 (contrat, registre du personnel) avant le virement.",
    "Erreur de virgule probable": "Contr\u00f4ler la saisie du montant : un d\u00e9calage de virgule (\u00d7100 ou \u00f7100) est probable.",
    "Montant tr\u00e8s sup\u00e9rieur \u00e0 l'habituel": "V\u00e9rifier primes, rappels ou erreur de saisie dans la fiche de paie, puis confirmer ou corriger le montant.",
    "IBAN partag\u00e9": "V\u00e9rifier que l'IBAN n'appartient pas \u00e0 un autre salari\u00e9 (risque de fraude ou d'erreur de saisie).",
    "Hausse inhabituelle": "V\u00e9rifier s'il existe une prime, un rappel, une promotion ou un 13e mois justifiant la hausse.",
    "Baisse inhabituelle": "V\u00e9rifier absences, solde de tout compte ou erreur de saisie.",
    "Profil de paiement inhabituel": "V\u00e9rifier la fiche de paie dans son ensemble, puis confirmer ou corriger le montant.",
}


def type_probable(row: pd.Series) -> str:
    """Type d'anomalie le plus probable pour un paiement signalé (le premier critère rempli l'emporte)."""
    montant = float(row["montant"])
    ecart = float(row["ecart_moyenne_salarie"])
    iban_change = int(row["iban_change_recent"]) == 1
    sans_historique = int(row.get("sans_historique", 0)) == 1
    if montant <= 0:
        return "Montant nul ou n\u00e9gatif"
    if int(row.get("nb_fiches_identiques", 1)) > 1:
        return "Doublon probable"
    if int(row.get("nb_salaries_meme_iban", 1)) > 1:
        return "IBAN partag\u00e9"
    if iban_change and ecart >= 1.5:
        return "IBAN modifi\u00e9 et montant \u00e9lev\u00e9"
    if iban_change:
        return "IBAN modifi\u00e9"
    if sans_historique:
        return "Salari\u00e9 sans historique"
    if ecart >= 50 or ecart <= 0.03:
        return "Erreur de virgule probable"
    if ecart >= 3:
        return "Montant tr\u00e8s sup\u00e9rieur \u00e0 l'habituel"
    if ecart >= 1.25:
        return "Hausse inhabituelle"
    if ecart <= 0.6:
        return "Baisse inhabituelle"
    return "Profil de paiement inhabituel"


def recommandation(type_anomalie: str) -> str:
    return RECOMMANDATIONS.get(type_anomalie, RECOMMANDATIONS["Profil de paiement inhabituel"])