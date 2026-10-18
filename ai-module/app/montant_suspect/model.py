"""Chargement, entraînement, scoring et évaluation du modèle Isolation Forest (montants suspects)."""
import datetime
import json
import logging
import os
import threading
import time
from typing import Dict, Optional, Tuple

import joblib
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

from .config import CONTAMINATION, FEATURES, N_ESTIMATORS, RANDOM_STATE, REGLE_IBAN_CHANGE, SCORE_THRESHOLD
from .features import compute_features
from .synthetic import generate

log = logging.getLogger(__name__)

MODEL_DIR = os.path.join(os.path.dirname(__file__), "models")
MODEL_PATH = os.path.join(MODEL_DIR, "iforest.pkl")
METRICS_PATH = os.path.join(MODEL_DIR, "metrics.json")

SOURCE_SYNTHETIQUE = "synthetique"
SOURCE_REEL = "reel"


class MontantSuspectModel:
    def __init__(self) -> None:
        self.model: IsolationForest | None = None
        self.scaler: StandardScaler | None = None
        self.version: str | None = None
        self.source: str = SOURCE_SYNTHETIQUE
        self.nb_entrainement: int = 0
        self.temps_entrainement: Optional[float] = None
        self._evaluation_lock = threading.Lock()
        self._evaluation_en_cours = False

    def load_or_train(self) -> None:
        """Charge le modèle persisté, ou l'entraîne sur des données synthétiques."""
        if os.path.exists(MODEL_PATH):
            data = joblib.load(MODEL_PATH)
            self.model = data["model"]
            self.scaler = data["scaler"]
            self.version = data["version"]
            self.source = data.get("source", SOURCE_SYNTHETIQUE)
            self.nb_entrainement = data.get("nbEntrainement", 0)
            self.temps_entrainement = data.get("tempsEntrainement")
        else:
            self.train_synthetic()

    def train(self, feature_df: pd.DataFrame, source: str = SOURCE_REEL) -> str:
        X = feature_df[FEATURES].to_numpy(dtype=float)
        t0 = time.perf_counter()
        scaler = StandardScaler().fit(X)
        model = IsolationForest(
            n_estimators=N_ESTIMATORS,
            contamination=CONTAMINATION,
            random_state=RANDOM_STATE,
        ).fit(scaler.transform(X))
        temps = time.perf_counter() - t0

        self.model = model
        self.scaler = scaler
        self.version = "iforest-" + datetime.datetime.now().strftime("%Y-%m-%d-%H%M%S")
        self.source = source
        self.nb_entrainement = len(X)
        self.temps_entrainement = temps

        os.makedirs(MODEL_DIR, exist_ok=True)
        joblib.dump(
            {"model": model, "scaler": scaler, "version": self.version, "source": source,
             "nbEntrainement": len(X), "tempsEntrainement": temps},
            MODEL_PATH,
        )
        # Les métriques de l'ancien modèle ne sont plus valables.
        if os.path.exists(METRICS_PATH):
            os.remove(METRICS_PATH)
        self.lancer_evaluation()
        return self.version

    def train_synthetic(self) -> str:
        paiements, _ = generate()
        return self.train(compute_features(paiements), SOURCE_SYNTHETIQUE)

    def score_isolation_forest(self, feature_df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray]:
        """Score brut d'Isolation Forest dans [0,1] et drapeaux, sans règle métier."""
        if self.model is None or self.scaler is None:
            self.load_or_train()
        X = self.scaler.transform(feature_df[FEATURES].to_numpy(dtype=float))
        decision = self.model.decision_function(X)
        # decision_function : négatif = anormal. On mappe vers [0,1] (score élevé = suspect).
        scores = 1.0 / (1.0 + np.exp(decision * 8.0))
        pred = self.model.predict(X)  # -1 = anomalie
        return scores, (pred == -1) | (scores >= SCORE_THRESHOLD)

    def score(self, feature_df: pd.DataFrame) -> Tuple[np.ndarray, np.ndarray]:
        """Décision en service : Isolation Forest + règle « IBAN modifié ».

        Isolation Forest ne repère pas un changement d'IBAN seul (variable binaire, montant normal) :
        rappel mesuré de 0 % sur ce type. La règle le signale systématiquement pour vérification.
        """
        scores, flags = self.score_isolation_forest(feature_df)
        if REGLE_IBAN_CHANGE:
            iban_change = feature_df["iban_change_recent"].to_numpy(dtype=float) == 1
            scores = np.where(iban_change, np.maximum(scores, SCORE_THRESHOLD), scores)
            flags = flags | iban_change
        return scores, flags

    # --- Métriques ---------------------------------------------------------

    def evaluer_maintenant(self) -> Dict:
        """Évalue le modèle courant et persiste le rapport dans metrics.json."""
        from .evaluation import evaluer

        if self.model is None:
            self.load_or_train()
        rapport = evaluer(self, self.source, self.nb_entrainement, self.temps_entrainement)
        if rapport["modelVersion"] == self.version:
            os.makedirs(MODEL_DIR, exist_ok=True)
            tmp = METRICS_PATH + ".tmp"
            with open(tmp, "w", encoding="utf-8") as f:
                json.dump(rapport, f, ensure_ascii=False, indent=2)
            os.replace(tmp, METRICS_PATH)
        return rapport

    def lancer_evaluation(self) -> None:
        """Évaluation en arrière-plan : l'entraînement répond sans attendre les métriques."""
        with self._evaluation_lock:
            if self._evaluation_en_cours:
                return
            self._evaluation_en_cours = True

        def tache():
            try:
                self.evaluer_maintenant()
            except Exception:  # noqa: BLE001 - une évaluation ratée ne doit pas arrêter le service
                log.exception("Évaluation du modèle en échec")
            finally:
                with self._evaluation_lock:
                    self._evaluation_en_cours = False

        threading.Thread(target=tache, name="evaluation-modele", daemon=True).start()

    def metriques(self) -> Optional[Dict]:
        """Rapport d'évaluation du modèle courant, ou None s'il est en cours de calcul."""
        if os.path.exists(METRICS_PATH):
            with open(METRICS_PATH, encoding="utf-8") as f:
                rapport = json.load(f)
            if rapport.get("modelVersion") == self.version:
                return rapport
        self.lancer_evaluation()
        return None

    @property
    def evaluation_en_cours(self) -> bool:
        return self._evaluation_en_cours
