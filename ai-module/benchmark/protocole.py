"""Protocole d'évaluation commun aux étapes A (évaluation) et B (comparaison).

- jeux générés par ``benchmark.dataset`` avec 5 graines (jeu de données ET modèle) ;
- découpage chronologique : mois 1–12 entraînement, 13–15 validation (choix du seuil), 16–18 test ;
- les variables sont calculées mois par mois, comme lorsque l'application contrôle le fichier d'un mois ;
- le modèle est entraîné par le code de production (mêmes hyperparamètres), sans écrire sur ``iforest.pkl``.
"""
from __future__ import annotations

import dataclasses
from typing import Dict, Tuple

import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler

from app.montant_suspect.config import CONTAMINATION, FEATURES, N_ESTIMATORS
from app.montant_suspect.features import compute_features
from app.montant_suspect.model import MontantSuspectModel

from . import dataset as ds

SEEDS = (42, 7, 11, 23, 31)
GRILLE_SEUILS = np.linspace(0.0, 1.0, 501)
COLONNES_META = ["label", "type_anomalie", "mois_index", "societe_id", "salarie_id", "evenement_normal",
                 "mois_depuis_dernier_paiement", "iban_valide"]


@dataclasses.dataclass
class JeuPrepare:
    seed: int
    entrainement: pd.DataFrame
    validation: pd.DataFrame
    test: pd.DataFrame


def _features_par_mois(df_part: pd.DataFrame) -> pd.DataFrame:
    """Variables du modèle calculées lot par lot (un lot = la paie d'un mois, toutes sociétés)."""
    morceaux = []
    for _, g in df_part.groupby("mois_index"):
        f = compute_features(ds.vers_paiements(g))
        f["label"] = g["label"].to_numpy()
        f["type_anomalie"] = g["type_anomalie"].to_numpy()
        f["mois_index"] = g["mois_index"].to_numpy()
        f["societe_id"] = g["societe_id"].to_numpy()
        f["salarie_id"] = g["salarie_id"].to_numpy()
        f["evenement_normal"] = g["evenement_normal"].to_numpy()
        f["mois_depuis_dernier_paiement"] = g["mois_depuis_dernier_paiement"].to_numpy()
        f["iban_valide"] = f["iban"].map(ds.iban_valide).to_numpy()
        morceaux.append(f)
    return pd.concat(morceaux, ignore_index=True)


def preparer(seed: int) -> JeuPrepare:
    parts = ds.decouper(ds.generer_jeu_de_donnees(seed))
    return JeuPrepare(seed, *(_features_par_mois(parts[n]) for n in ("entrainement", "validation", "test")))


def entrainer_modele_en_service(train: pd.DataFrame, seed: int) -> MontantSuspectModel:
    """Isolation Forest identique à la production (200 arbres, contamination 1 %), hors persistance."""
    X = train[FEATURES].to_numpy(dtype=float)
    scaler = StandardScaler().fit(X)
    modele = MontantSuspectModel()
    modele.scaler = scaler
    modele.model = IsolationForest(n_estimators=N_ESTIMATORS, contamination=CONTAMINATION,
                                   random_state=seed).fit(scaler.transform(X))
    modele.nb_entrainement = len(X)
    return modele


# --- Décisions et métriques --------------------------------------------------------------------------

def matrice(y: np.ndarray, flags: np.ndarray) -> Tuple[int, int, int, int]:
    y, f = np.asarray(y).astype(bool), np.asarray(flags).astype(bool)
    return int((y & f).sum()), int((~y & f).sum()), int((y & ~f).sum()), int((~y & ~f).sum())


def metriques_decision(y: np.ndarray, flags: np.ndarray) -> Dict[str, float]:
    tp, fp, fn, tn = matrice(y, flags)
    precision = tp / (tp + fp) if tp + fp else 0.0
    rappel = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * precision * rappel / (precision + rappel) if precision + rappel else 0.0
    return {"precision": precision, "rappel": rappel, "f1": f1, "tp": tp, "fp": fp, "fn": fn, "tn": tn,
            "fp_pour_1000": fp / len(y) * 1000, "alertes_pour_1000": (tp + fp) / len(y) * 1000}


def f1_selon_seuil(y: np.ndarray, score: np.ndarray, forces: np.ndarray | None = None) -> np.ndarray:
    """F1 pour chaque seuil de la grille ; ``forces`` = fiches toujours signalées (règle métier)."""
    y = np.asarray(y).astype(bool)
    forces = np.zeros_like(y) if forces is None else np.asarray(forces).astype(bool)
    resultat = np.empty(len(GRILLE_SEUILS))
    for i, t in enumerate(GRILLE_SEUILS):
        resultat[i] = metriques_decision(y, (score >= t) | forces)["f1"]
    return resultat


def choisir_seuil(y_val: np.ndarray, score_val: np.ndarray, forces_val: np.ndarray | None = None) -> float:
    """Seuil maximisant le F1 sur la VALIDATION (au milieu du plateau en cas d'égalité)."""
    f1 = f1_selon_seuil(y_val, score_val, forces_val)
    meilleurs = np.flatnonzero(np.isclose(f1, f1.max()))
    return float(GRILLE_SEUILS[meilleurs[len(meilleurs) // 2]])
