"""Évalue le modèle Isolation Forest sur des données synthétiques.

Affiche précision / rappel / F1 et compare avec une règle simple
« montant > 2 x la moyenne des 6 derniers mois ».

Usage : python -m scripts.evaluate  (depuis le dossier ai-module/)
"""
import numpy as np

from app.montant_suspect.features import compute_features
from app.montant_suspect.model import MontantSuspectModel
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


def main() -> None:
    paiements, labels = generate(seed=7)
    df = compute_features(paiements)

    model = MontantSuspectModel()
    model.train_synthetic()  # entraîné sur un autre tirage (seed=42)
    _, flags = model.score(df)

    # Règle simple de référence : montant > 2 x moyenne des 6 derniers mois.
    regle = []
    for p in paiements:
        hist = p["historique"]
        moyenne = float(np.mean(hist)) if hist else float(p["montant"])
        regle.append(1 if p["montant"] > 2.0 * moyenne else 0)

    p_ia, r_ia, f_ia = _metrics(labels, flags.astype(int))
    p_rg, r_rg, f_rg = _metrics(labels, regle)

    print("=== Isolation Forest (montants suspects) ===")
    print(f"Précision={p_ia:.3f}  Rappel={r_ia:.3f}  F1={f_ia:.3f}")
    print("=== Règle simple (montant > 2x moyenne) ===")
    print(f"Précision={p_rg:.3f}  Rappel={r_rg:.3f}  F1={f_rg:.3f}")


if __name__ == "__main__":
    main()
