"""Prépare la version candidate « hybride optimisé » (règles métier + Isolation Forest à variables retenues).

Usage (depuis ai-module/) :  python -m benchmark.exporter_candidat

Le candidat est écrit dans benchmark/candidat_v2/ et N'EST PAS ACTIVÉ : le modèle en service
(app/montant_suspect/models/iforest.pkl) et l'API ne sont pas modifiés. Variables et seuil sont choisis sur la
validation seule (graine 42, entraînement sur les mois 1 à 12), comme dans le protocole du rapport.
"""
import datetime
import json
import os

import joblib

from . import modeles as md
from . import protocole as pr
from . import reporting as rp

DOSSIER = os.path.join(os.path.dirname(os.path.abspath(__file__)), "candidat_v2")
SEED = 42


def main() -> None:
    comparaison = rp.lire_resultats().get("comparaison")
    if comparaison is None:
        raise SystemExit("Lancez d'abord : python -m benchmark.compare")
    nb_graines = comparaison["variablesRetenues"]["nbGraines"]
    retenues = [f for f in md.FEATURES_COLLECTIF
                if comparaison["variablesRetenues"]["parVariable"][f] > nb_graines / 2]

    jeu = pr.preparer(SEED)
    modele = md.IsolationForestGenerique(retenues, SEED).fit(jeu.entrainement)
    regles_v = md.regles_metier(jeu.validation, relatif=True)["declenchee"].to_numpy()
    regles_t = md.regles_metier(jeu.test, relatif=True)["declenchee"].to_numpy()
    seuil = pr.choisir_seuil(jeu.validation["label"].to_numpy(), modele.score(jeu.validation), regles_v)
    flags = regles_t | (modele.score(jeu.test) >= seuil)
    m = pr.metriques_decision(jeu.test["label"].to_numpy(), flags)

    version = "hybride-optimise-v2-" + datetime.date.today().isoformat()
    meta = {
        "version": version, "statut": "candidat, non active", "graine": SEED,
        "variables": retenues, "seuilScore": seuil,
        "regles": {"iban_invalide": True, "montant_non_positif": True, "doublon": True,
                   "montant_double_ecart_relatif_societe": 2.0},
        "nbEntrainement": int(len(jeu.entrainement)), "hyperparametres": {"nbArbres": modele.model.n_estimators,
                                                                          "contamination": 0.01},
        "testGraine42": {k: round(m[k], 4) for k in ("precision", "rappel", "f1", "fp_pour_1000")},
        "moyenne5Graines": {k: round(comparaison["approches"]["hybride_optimise"]["metriques"][k]["moyenne"], 4)
                            for k in ("precision", "rappel", "f1", "prAuc", "fpPour1000")},
        "avertissement": "Entraîné et évalué sur des données synthétiques ; à confirmer sur des données réelles.",
    }
    os.makedirs(DOSSIER, exist_ok=True)
    joblib.dump({"model": modele.model, "scaler": modele.scaler, **meta}, os.path.join(DOSSIER, "hybride_optimise_v2.joblib"))
    with open(os.path.join(DOSSIER, "metadonnees.json"), "w", encoding="utf-8") as f:
        json.dump(meta, f, ensure_ascii=False, indent=2)
    rp.maj_resultats("candidat", meta)
    print(f"Candidat {version} écrit dans {DOSSIER} : variables {retenues}, seuil {seuil:.3f}, "
          f"F1 test (graine {SEED}) {m['f1']:.3f}. NON ACTIVÉ.")


if __name__ == "__main__":
    main()
