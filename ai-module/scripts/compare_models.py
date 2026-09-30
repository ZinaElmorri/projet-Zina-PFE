"""Compare Isolation Forest à d'autres approches sur les mêmes données synthétiques :
règle simple, Local Outlier Factor (LOF), One-Class SVM.

Objectif : disposer de chiffres vérifiables (précision/rappel/F1 + temps d'entraînement)
pour justifier le choix d'Isolation Forest (ex. soutenance de PFE).

Usage : python -m scripts.compare_models  (depuis le dossier ai-module/)
"""
import time

import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.neighbors import LocalOutlierFactor
from sklearn.preprocessing import StandardScaler
from sklearn.svm import OneClassSVM

from app.montant_suspect.config import FEATURES, N_ESTIMATORS, RANDOM_STATE
from app.montant_suspect.features import compute_features
from app.montant_suspect.synthetic import generate


def _metrics(y_true, y_pred):
    y_true = np.asarray(y_true, dtype=int)
    y_pred = np.asarray(y_pred, dtype=int)
    tp = int(np.sum((y_pred == 1) & (y_true == 1)))
    fp = int(np.sum((y_pred == 1) & (y_true == 0)))
    fn = int(np.sum((y_pred == 0) & (y_true == 1)))
    precision = tp / (tp + fp) if (tp + fp) else 0.0
    rappel = tp / (tp + fn) if (tp + fn) else 0.0
    f1 = (2 * precision * rappel / (precision + rappel)) if (precision + rappel) else 0.0
    return precision, rappel, f1


def _regle_simple(paiements):
    regle = []
    for p in paiements:
        hist = p["historique"]
        moyenne = float(np.mean(hist)) if hist else float(p["montant"])
        regle.append(1 if p["montant"] > 2.0 * moyenne else 0)
    return regle


def main() -> None:
    # Jeu d'entraînement et jeu de test distincts (pas de fuite de données).
    train_paiements, _ = generate(seed=42)
    test_paiements, test_labels = generate(seed=7)

    X_train = compute_features(train_paiements)[FEATURES].to_numpy(dtype=float)
    df_test = compute_features(test_paiements)
    X_test = df_test[FEATURES].to_numpy(dtype=float)

    scaler = StandardScaler().fit(X_train)
    X_train_s = scaler.transform(X_train)
    X_test_s = scaler.transform(X_test)

    resultats = []

    # --- Règle simple (baseline) ---
    t0 = time.perf_counter()
    pred_regle = _regle_simple(test_paiements)
    resultats.append(("Règle simple (montant > 2x moyenne)", *_metrics(test_labels, pred_regle),
                       0.0, time.perf_counter() - t0))

    # --- Isolation Forest (modèle retenu) ---
    t0 = time.perf_counter()
    iforest = IsolationForest(n_estimators=N_ESTIMATORS, contamination=0.01, random_state=RANDOM_STATE)
    iforest.fit(X_train_s)
    t_train = time.perf_counter() - t0
    t0 = time.perf_counter()
    pred_if = (iforest.predict(X_test_s) == -1).astype(int)
    resultats.append(("Isolation Forest", *_metrics(test_labels, pred_if), t_train, time.perf_counter() - t0))

    # --- Local Outlier Factor (novelty=True pour pouvoir scorer un jeu de test séparé) ---
    t0 = time.perf_counter()
    lof = LocalOutlierFactor(n_neighbors=20, contamination=0.01, novelty=True)
    lof.fit(X_train_s)
    t_train = time.perf_counter() - t0
    t0 = time.perf_counter()
    pred_lof = (lof.predict(X_test_s) == -1).astype(int)
    resultats.append(("Local Outlier Factor", *_metrics(test_labels, pred_lof), t_train, time.perf_counter() - t0))

    # --- One-Class SVM ---
    t0 = time.perf_counter()
    ocsvm = OneClassSVM(nu=0.01, kernel="rbf", gamma="scale")
    ocsvm.fit(X_train_s)
    t_train = time.perf_counter() - t0
    t0 = time.perf_counter()
    pred_svm = (ocsvm.predict(X_test_s) == -1).astype(int)
    resultats.append(("One-Class SVM", *_metrics(test_labels, pred_svm), t_train, time.perf_counter() - t0))

    print(f"{'Modèle':<38}{'Précision':>10}{'Rappel':>10}{'F1':>10}{'Train(s)':>12}{'Predict(s)':>12}")
    for nom, p, r, f1, t_train, t_pred in resultats:
        print(f"{nom:<38}{p:>10.3f}{r:>10.3f}{f1:>10.3f}{t_train:>12.4f}{t_pred:>12.4f}")


if __name__ == "__main__":
    main()
