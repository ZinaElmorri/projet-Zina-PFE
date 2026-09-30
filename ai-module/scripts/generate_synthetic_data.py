"""Génère un jeu de données synthétiques et l'écrit en CSV.

Usage : python -m scripts.generate_synthetic_data [chemin_csv]
(depuis le dossier ai-module/)
"""
import csv
import sys

from app.montant_suspect.synthetic import generate


def main() -> None:
    chemin = sys.argv[1] if len(sys.argv) > 1 else "synthetic_paiements.csv"
    paiements, labels = generate()
    with open(chemin, "w", newline="", encoding="utf-8") as f:
        writer = csv.writer(f)
        writer.writerow(["ficheId", "salarieId", "clientId", "montant", "iban", "ibanChange", "historique", "label"])
        for p, label in zip(paiements, labels):
            writer.writerow([
                p["ficheId"], p["salarieId"], p["clientId"], p["montant"],
                p["iban"], p["ibanChange"], "|".join(str(x) for x in p["historique"]), label,
            ])
    print(f"{len(paiements)} paiements écrits dans {chemin} "
          f"({sum(labels)} anomalies injectées).")


if __name__ == "__main__":
    main()
