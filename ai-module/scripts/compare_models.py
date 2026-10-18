"""Compare le modèle en service à d'autres approches (règle simple, LOF, One-Class SVM).

Même protocole que l'écran « Intelligence artificielle » : 5 jeux de test synthétiques étiquetés
de 1 000 paiements (3 % d'anomalies injectées), métriques en moyenne ± écart-type.

Usage : python -m scripts.compare_models [--json rapport.json]  (depuis le dossier ai-module/)
"""
import argparse
import json

from app.montant_suspect.evaluation import evaluer
from app.montant_suspect.model import MontantSuspectModel

COLONNES = (("accuracy", "Accuracy"), ("balancedAccuracy", "Acc. équil."), ("precision", "Précision"),
            ("rappel", "Rappel"), ("f1", "F1"), ("rocAuc", "ROC-AUC"), ("prAuc", "PR-AUC"))


def afficher(rapport: dict) -> None:
    print(f"Modèle {rapport['modelVersion']} — entraîné sur {rapport['entrainement']['nbPaiements']} "
          f"paiements ({rapport['entrainement']['source']})")
    p = rapport["protocole"]
    print(f"Test : {p['nbTirages']} tirages x {p['paiementsParTirage']} paiements, "
          f"{p['tauxAnomalies']:.0%} d'anomalies injectées\n")
    print(f"{'Modèle':<52}" + "".join(f"{titre:>13}" for _, titre in COLONNES))
    for m in rapport["modeles"]:
        cellules = "".join(
            f"{m['metriques'][cle]['moyenne']:>7.3f}±{m['metriques'][cle]['ecartType']:<5.3f}" for cle, _ in COLONNES)
        print(f"{m['nom']:<52}{cellules}")
    print("\nMatrice de confusion (cumul des tirages) et rappel par type d'anomalie :")
    for m in rapport["modeles"]:
        c = m["matriceConfusion"]
        types = ", ".join(f"{t['libelle']} {t['rappel']:.0%}" for t in m["rappelParType"] if t["rappel"] is not None)
        print(f"- {m['nom']} : VP={c['vraisPositifs']} FP={c['fauxPositifs']} "
              f"FN={c['fauxNegatifs']} VN={c['vraisNegatifs']} | {types}")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--json", help="Écrit aussi le rapport complet dans ce fichier")
    args = parser.parse_args()

    modele = MontantSuspectModel()
    modele.load_or_train()
    rapport = evaluer(modele, modele.source, modele.nb_entrainement, modele.temps_entrainement)
    afficher(rapport)
    if args.json:
        with open(args.json, "w", encoding="utf-8") as f:
            json.dump(rapport, f, ensure_ascii=False, indent=2)
        print(f"\nRapport écrit dans {args.json}")


if __name__ == "__main__":
    main()
