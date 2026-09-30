"""Chargement, entraînement et scoring du modèle Isolation Forest (montants suspects)."""
import datetime
import os
from typing import Tuple

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

from .config import CONTAMINATION, FEATURES, N_ESTIMATORS, RANDOM_STATE, SCORE_THRESHOLD
from .features import compute_features
from .synthetic import generate

MODEL_DIR = os.path.join(os.path.dirname(__file__), "models")
MODEL_PATH = os.path.join(MODEL_DIR, "iforest.pkl")


class MontantSuspectModel:
    def __init__(self) -> None:
        self.model: IsolationForest | None = None
        self.scaler: StandardScaler | None = None
        self.version: str | None = None

    def load_or_train(self) -> None:
        """Charge le modèle persisté, ou l'entraîne sur des données synthétiques."""
        if os.path.exists(MODEL_PATH):
            data = joblib.load(MODEL_PATH)
            self.model = data["model"]
            self.scaler = data["scaler"]
            self.version = data["version"]
        else:
            self.train_synthetic()

    def train(self, feature_df: pd.DataFrame) -> str:
        X = feature_df[FEATURES].to_numpy(dtype=float)
        scaler = StandardScaler().fit(X)
        model = IsolationForest(
            n_estimators=N_ESTIMATORS,
            contamination=CONTAMINATION,
            random_state=RANDOM_STATE,
        ).fit(scaler.transform(X))

        self.model = model
        self.scaler = scaler
        self.version = "iforest-" + datetime.date.today().isoformat()

        os.makedirs(MODEL_DIR, exist_ok=True)
        joblib.dump(
            {"model": model, "scaler": scaler, "version": self.version},
            MODEL_PATH,
        )
        return self.version

    def train_synthetic(self) -> str:
        paiements, _ = generate()
        return self.train(compute_features(paiements))

    def score(self, feature_df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray]:
        """Retourne (scores dans [0,1], drapeaux isAnomaly)."""
        if self.model is None or self.scaler is None:
            self.load_or_train()
        X = self.scaler.transform(feature_df[FEATURES].to_numpy(dtype=float))
        decision = self.model.decision_function(X)
        # decision_function : négatif = anormal. On mappe vers [0,1] (score élevé = suspect).
        scores = 1.0 / (1.0 + np.exp(decision * 8.0))
        pred = self.model.predict(X)  # -1 = anomalie
        is_anomaly = (pred == -1) | (scores >= SCORE_THRESHOLD)
        return scores, is_anomaly
