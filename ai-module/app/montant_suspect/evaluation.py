"""Évaluation du modèle de détection des montants suspects.

Protocole : plusieurs jeux de test synthétiques étiquetés (anomalies injectées, tirages indépendants
du jeu d'entraînement), mêmes règles de décision qu'en production, comparaison avec une règle simple,
Local Outlier Factor et One-Class SVM. Les résultats sont donnés en moyenne ± écart-type sur les tirages.
"""
import datetime
import time
from typing import Callable, Dict, List, Optional, Tuple

import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.metrics import (
    accuracy_score,
    average_precision_score,
    balanced_accuracy_score,
    confusion_matrix,
    f1_score,
    precision_score,
    recall_score,
    roc_auc_score,
)
from sklearn.neighbors import LocalOutlierFactor
from sklearn.preprocessing import StandardScaler
from sklearn.svm import OneClassSVM

from .config import (CONTAMINATION, FEATURES, HISTORY_MONTHS, N_ESTIMATORS, RANDOM_STATE,
                     REGLE_IBAN_CHANGE, SCORE_THRESHOLD)
from .features import compute_features
from .synthetic import TYPES_ANOMALIES, generate_with_types

SEEDS_TEST = (7, 11, 23, 31, 47)
N_CLIENTS_TEST = 20
SALARIES_PAR_CLIENT_TEST = 50
TAUX_ANOMALIES_TEST = 0.03
METRIQUES = ("accuracy", "balancedAccuracy", "precision", "rappel", "f1", "rocAuc", "prAuc")

# Prédicteur : X normalisé par le modèle lui-même -> (scores « plus haut = plus suspect », prédictions 0/1)
Predicteur = Callable[[object], Tuple[np.ndarray, np.ndarray]]


def _metriques(y: np.ndarray, pred: np.ndarray, scores: np.ndarray) -> Dict[str, float]:
    return {
        "accuracy": float(accuracy_score(y, pred)),
        "balancedAccuracy": float(balanced_accuracy_score(y, pred)),
        "precision": float(precision_score(y, pred, zero_division=0)),
        "rappel": float(recall_score(y, pred, zero_division=0)),
        "f1": float(f1_score(y, pred, zero_division=0)),
        "rocAuc": float(roc_auc_score(y, scores)),
        "prAuc": float(average_precision_score(y, scores)),
    }


def _jeux_de_test() -> List[Tuple[object, np.ndarray, List[Optional[str]], List[Dict]]]:
    jeux = []
    for seed in SEEDS_TEST:
        paiements, labels, types = generate_with_types(
            N_CLIENTS_TEST, SALARIES_PAR_CLIENT_TEST, TAUX_ANOMALIES_TEST, seed)
        jeux.append((compute_features(paiements), np.asarray(labels, dtype=int), types, paiements))
    return jeux


def _modeles_de_comparaison() -> Dict[str, Tuple[str, Callable, float]]:
    """Entraîne les modèles de référence sur le jeu synthétique standard (seed 42)."""
    paiements, _ = _jeu_entrainement_reference()
    X_train = compute_features(paiements)[FEATURES].to_numpy(dtype=float)
    scaler = StandardScaler().fit(X_train)
    Xs = scaler.transform(X_train)

    t0 = time.perf_counter()
    lof = LocalOutlierFactor(n_neighbors=20, contamination=CONTAMINATION, novelty=True).fit(Xs)
    t_lof = time.perf_counter() - t0

    t0 = time.perf_counter()
    ocsvm = OneClassSVM(nu=CONTAMINATION, kernel="rbf", gamma="scale").fit(Xs)
    t_svm = time.perf_counter() - t0

    def predire_lof(df):
        X = scaler.transform(df[FEATURES].to_numpy(dtype=float))
        return -lof.score_samples(X), (lof.predict(X) == -1).astype(int)

    def predire_svm(df):
        X = scaler.transform(df[FEATURES].to_numpy(dtype=float))
        return -ocsvm.decision_function(X), (ocsvm.predict(X) == -1).astype(int)

    return {
        "lof": ("Local Outlier Factor", predire_lof, t_lof),
        "ocsvm": ("One-Class SVM", predire_svm, t_svm),
    }


def _jeu_entrainement_reference():
    from .synthetic import generate
    return generate(seed=RANDOM_STATE)


def _regle_simple(df, paiements: List[Dict]) -> Tuple[np.ndarray, np.ndarray]:
    """Règle métier de référence : montant > 2 x la moyenne des derniers mois."""
    ratios = []
    for p in paiements:
        hist = p["historique"][-HISTORY_MONTHS:]
        moyenne = float(np.mean(hist)) if hist else float(p["montant"])
        ratios.append(float(p["montant"]) / moyenne if moyenne else 1.0)
    ratios = np.asarray(ratios)
    return ratios, (ratios > 2.0).astype(int)


def _evaluer_un(nom: str, cle: str, predire, jeux, en_service: bool, t_train: Optional[float]) -> Dict:
    par_tirage = {m: [] for m in METRIQUES}
    tp = fp = fn = tn = 0
    detectes_par_type = {t: 0 for t in TYPES_ANOMALIES}
    total_par_type = {t: 0 for t in TYPES_ANOMALIES}
    t_pred = 0.0

    for df, y, types, paiements in jeux:
        t0 = time.perf_counter()
        scores, pred = predire(df, paiements)
        t_pred += time.perf_counter() - t0
        for m, v in _metriques(y, pred, scores).items():
            par_tirage[m].append(v)
        c_tn, c_fp, c_fn, c_tp = confusion_matrix(y, pred, labels=[0, 1]).ravel()
        tn, fp, fn, tp = tn + int(c_tn), fp + int(c_fp), fn + int(c_fn), tp + int(c_tp)
        for t, p in zip(types, pred):
            if t:
                total_par_type[t] += 1
                detectes_par_type[t] += int(p)

    return {
        "cle": cle,
        "nom": nom,
        "enService": en_service,
        "metriques": {m: {"moyenne": round(float(np.mean(v)), 4), "ecartType": round(float(np.std(v)), 4)}
                      for m, v in par_tirage.items()},
        "matriceConfusion": {"vraisPositifs": tp, "fauxPositifs": fp, "fauxNegatifs": fn, "vraisNegatifs": tn},
        "rappelParType": [
            {"type": t, "libelle": TYPES_ANOMALIES[t], "detectees": detectes_par_type[t],
             "total": total_par_type[t],
             "rappel": round(detectes_par_type[t] / total_par_type[t], 4) if total_par_type[t] else None}
            for t in TYPES_ANOMALIES
        ],
        "tempsEntrainementS": None if t_train is None else round(t_train, 4),
        "tempsPredictionMs": round(t_pred / len(jeux) * 1000, 2),
    }


def evaluer(modele, source: str, nb_entrainement: int, temps_entrainement: Optional[float]) -> Dict:
    """Évalue le modèle en service (``MontantSuspectModel``) et les modèles de comparaison."""
    version = modele.version
    jeux = _jeux_de_test()

    def predire_prod(df, _paiements):
        scores, flags = modele.score(df)
        return np.asarray(scores), np.asarray(flags).astype(int)

    def predire_if_seul(df, _paiements):
        scores, flags = modele.score_isolation_forest(df)
        return np.asarray(scores), np.asarray(flags).astype(int)

    nom_prod = ("Isolation Forest + règle IBAN modifié (en service)" if REGLE_IBAN_CHANGE
                else "Isolation Forest (en service)")
    resultats = [_evaluer_un(nom_prod, "en_service", predire_prod, jeux, True, temps_entrainement)]
    if REGLE_IBAN_CHANGE:
        resultats.append(_evaluer_un("Isolation Forest seul", "isolation_forest",
                                     predire_if_seul, jeux, False, temps_entrainement))
    resultats.append(_evaluer_un("Règle simple (montant > 2x moyenne)", "regle",
                                 _regle_simple, jeux, False, 0.0))
    for cle, (nom, predire, t_train) in _modeles_de_comparaison().items():
        resultats.append(_evaluer_un(nom, cle, lambda df, _p, f=predire: f(df), jeux, False, t_train))

    return {
        "date": datetime.datetime.now().isoformat(timespec="seconds"),
        "modelVersion": version,
        "entrainement": {"source": source, "nbPaiements": nb_entrainement},
        "configuration": {
            "algorithme": "Isolation Forest" + (" + règle IBAN modifié" if REGLE_IBAN_CHANGE else ""),
            "regleIbanModifie": REGLE_IBAN_CHANGE,
            "seuilScore": SCORE_THRESHOLD,
            "contamination": CONTAMINATION,
            "nbArbres": N_ESTIMATORS,
            "historiqueMois": HISTORY_MONTHS,
            "randomState": RANDOM_STATE,
            "variables": FEATURES,
        },
        "protocole": {
            "jeuTest": "Synthétique étiqueté (anomalies injectées), indépendant du jeu d'entraînement",
            "nbTirages": len(SEEDS_TEST),
            "seeds": list(SEEDS_TEST),
            "paiementsParTirage": N_CLIENTS_TEST * SALARIES_PAR_CLIENT_TEST,
            "tauxAnomalies": TAUX_ANOMALIES_TEST,
            "regleDecision": f"anomalie si prédiction Isolation Forest = -1 ou score >= {SCORE_THRESHOLD}"
                             + (" ou IBAN modifié" if REGLE_IBAN_CHANGE else ""),
            "comparaison": "Règle simple, LOF et One-Class SVM entraînés sur le jeu synthétique standard (seed 42)",
        },
        "modeles": resultats,
    }
