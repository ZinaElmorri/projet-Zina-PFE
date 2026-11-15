"""Approches comparées à l'étape B : règles métier, Isolation Forest (et variantes), LOF, One-Class SVM.

Tous les modèles apprennent sur les mêmes variables (``FEATURES`` de la production) ; seule la variante
« calendrier » ajoute l'indicateur de décembre, connu à partir de la période de paie.
"""
from __future__ import annotations

from typing import List, Optional

import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.neighbors import LocalOutlierFactor
from sklearn.preprocessing import StandardScaler
from sklearn.svm import OneClassSVM

from app.montant_suspect.config import CONTAMINATION, FEATURES, N_ESTIMATORS

FEATURES_CALENDRIER = FEATURES + ["est_decembre"]
FEATURES_COLLECTIF = FEATURES + ["ecart_relatif_societe"]
TAILLE_MAX_OCSVM = 10_000  # le coût du One-Class SVM croît comme le carré de l'effectif d'entraînement

NOMS_REGLES = {
    "iban_invalide": "IBAN invalide (clé modulo 97)",
    "montant_non_positif": "Montant négatif ou nul",
    "doublon": "Doublon (même salarié, IBAN et montant dans le mois)",
    "montant_double_moyenne": "Montant supérieur à 2 fois la moyenne des 6 derniers mois",
}


def regles_metier(df: pd.DataFrame, relatif: bool = False) -> pd.DataFrame:
    """Contrôles métier existants de l'application (IBAN, montant, doublon) + règle « 2 × la moyenne ».

    Le doublon est signalé sur les deux fiches du couple (même mois, même salarié, IBAN et montant).
    Avec ``relatif=True``, la règle « 2 × la moyenne » compare l'écart du salarié à celui de sa société pour le
    mois, ce qui évite de signaler tout un effectif lors d'un 13e mois ou d'une augmentation générale.
    """
    regles = pd.DataFrame(index=df.index)
    regles["iban_invalide"] = ~df["iban_valide"].astype(bool)
    regles["montant_non_positif"] = df["montant"] <= 0
    taille_groupe = df.groupby(["mois_index", "salarie_id", "iban", "montant"])["ficheId"].transform("size")
    regles["doublon"] = taille_groupe > 1
    colonne = "ecart_relatif_societe" if relatif else "ecart_moyenne_salarie"
    regles["montant_double_moyenne"] = df[colonne] > 2.0
    regles["declenchee"] = regles.any(axis=1)
    return regles


class IsolationForestGenerique:
    """Isolation Forest aux hyperparamètres de production, sur des variables au choix.

    Le score est le même qu'en production : ``1 / (1 + exp(8 × decision_function))`` (0 à 1, élevé = suspect).
    """

    def __init__(self, features: List[str], seed: int):
        self.features, self.seed = list(features), seed
        self.scaler: Optional[StandardScaler] = None
        self.model: Optional[IsolationForest] = None

    def fit(self, df: pd.DataFrame) -> "IsolationForestGenerique":
        X = df[self.features].to_numpy(dtype=float)
        self.scaler = StandardScaler().fit(X)
        self.model = IsolationForest(n_estimators=N_ESTIMATORS, contamination=CONTAMINATION,
                                     random_state=self.seed).fit(self.scaler.transform(X))
        return self

    def score(self, df: pd.DataFrame) -> np.ndarray:
        decision = self.model.decision_function(self.scaler.transform(df[self.features].to_numpy(dtype=float)))
        return 1.0 / (1.0 + np.exp(decision * 8.0))


class LocalOutlierFactorModele:
    def __init__(self, seed: int):
        self.seed = seed

    def fit(self, df: pd.DataFrame) -> "LocalOutlierFactorModele":
        X = df[FEATURES].to_numpy(dtype=float)
        self.scaler = StandardScaler().fit(X)
        self.model = LocalOutlierFactor(n_neighbors=20, contamination=CONTAMINATION, novelty=True,
                                        n_jobs=-1).fit(self.scaler.transform(X))
        return self

    def score(self, df: pd.DataFrame) -> np.ndarray:
        return -self.model.score_samples(self.scaler.transform(df[FEATURES].to_numpy(dtype=float)))


class OneClassSvmModele:
    def __init__(self, seed: int):
        self.seed = seed

    def fit(self, df: pd.DataFrame) -> "OneClassSvmModele":
        X = df[FEATURES].to_numpy(dtype=float)
        if len(X) > TAILLE_MAX_OCSVM:
            X = X[np.random.default_rng(self.seed).choice(len(X), TAILLE_MAX_OCSVM, replace=False)]
        self.scaler = StandardScaler().fit(X)
        self.model = OneClassSVM(nu=CONTAMINATION, kernel="rbf", gamma="scale").fit(self.scaler.transform(X))
        return self

    def score(self, df: pd.DataFrame) -> np.ndarray:
        return -self.model.decision_function(self.scaler.transform(df[FEATURES].to_numpy(dtype=float)))
