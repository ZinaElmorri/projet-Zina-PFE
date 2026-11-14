"""Étape A : évaluation rigoureuse du modèle de détection (livrables du rapport).

Usage (depuis ai-module/) :  python -m benchmark.evaluate_full

Protocole : 5 graines ; entraînement mois 1–12 ; seuil choisi sur la validation (mois 13–15) ; résultats sur le
test (mois 16–18) ; moyenne ± écart-type sur les graines.
"""
from __future__ import annotations

import os
import time
from typing import Dict, List

import numpy as np
from sklearn.metrics import average_precision_score, precision_recall_curve, roc_auc_score, roc_curve

from app.montant_suspect.config import FEATURES, N_ESTIMATORS, SCORE_THRESHOLD

from . import dataset as ds
from . import protocole as pr
from . import reporting as rp

CONFIGS = {
    "en_service": "Décision actuelle en production (score ≥ 0,5 ou IBAN modifié)",
    "if_seul": "Isolation Forest seul (seuil choisi sur la validation)",
    "if_regle_iban": "Isolation Forest + règle IBAN modifié (seuil choisi sur la validation)",
}
RETENU = "en_service"
# La prédiction native de l'Isolation Forest (-1) équivaut à un score >= 0,5 (décision < 0) : le seuil configuré à 0,6
# (SCORE_THRESHOLD) est donc sans effet, la décision en service se confond avec « score >= 0,5 ou IBAN modifié ».
SEUIL_EFFECTIF = 0.5
METRIQUES = ("precision", "rappel", "f1", "rocAuc", "prAuc", "fpPour1000")
GRILLE_RAPPEL = np.linspace(0, 1, 201)
GRILLE_FPR = np.linspace(0, 1, 201)


def _stat(valeurs) -> Dict[str, float]:
    v = np.asarray(valeurs, dtype=float)
    return {"moyenne": float(v.mean()), "ecartType": float(v.std(ddof=0))}


def _interp_pr(y, score):
    p, r, _ = precision_recall_curve(y, score)
    ordre = np.argsort(r)
    return np.interp(GRILLE_RAPPEL, r[ordre], p[ordre])


def calculer() -> Dict:
    brut = {c: {"valeurs": {m: [] for m in METRIQUES}, "matrice": [], "seuils": [], "par_type": [],
                "pr": [], "roc": [], "y_test": [], "score_test": [], "f1_val": [], "f1_test": [],
                "fp_causes": [], "periodes": []}
            for c in CONFIGS}
    infos = {"nbEntrainement": [], "tempsEntrainementS": [], "tempsPredictionMsPour1000": []}

    for seed in pr.SEEDS:
        t0 = time.perf_counter()
        jeu = pr.preparer(seed)
        print(f"  graine {seed} : données prêtes en {time.perf_counter() - t0:.1f} s "
              f"({len(jeu.entrainement)} / {len(jeu.validation)} / {len(jeu.test)} fiches)")
        t0 = time.perf_counter()
        modele = pr.entrainer_modele_en_service(jeu.entrainement, seed)
        infos["tempsEntrainementS"].append(time.perf_counter() - t0)
        infos["nbEntrainement"].append(len(jeu.entrainement))

        sorties = {}
        for nom, d in (("validation", jeu.validation), ("test", jeu.test)):
            t0 = time.perf_counter()
            score_service, flags_service = modele.score(d)
            if nom == "test":
                infos["tempsPredictionMsPour1000"].append((time.perf_counter() - t0) / len(d) * 1e6)
            score_if, _ = modele.score_isolation_forest(d)
            force = d["iban_change_recent"].to_numpy() == 1
            sorties[nom] = {
                "y": d["label"].to_numpy(), "types": d["type_anomalie"].to_numpy(),
                "en_service": (np.asarray(score_service), np.asarray(flags_service), None),
                "if_seul": (np.asarray(score_if), None, None),
                "if_regle_iban": (np.where(force, 1.0, score_if), None, force),
                "score_if": np.asarray(score_if), "force": force,
            }

        for cle in CONFIGS:
            yv, yt = sorties["validation"]["y"], sorties["test"]["y"]
            if cle == "en_service":
                seuil = SEUIL_EFFECTIF
                flags_t = sorties["test"][cle][1]
                score_t = sorties["test"][cle][0]
                f1_val = pr.f1_selon_seuil(yv, sorties["validation"][cle][0])
                f1_test = pr.f1_selon_seuil(yt, score_t)
            else:
                score_if_v, score_if_t = sorties["validation"]["score_if"], sorties["test"]["score_if"]
                force_v = sorties["validation"]["force"] if cle == "if_regle_iban" else None
                force_t = sorties["test"]["force"] if cle == "if_regle_iban" else None
                seuil = pr.choisir_seuil(yv, score_if_v, force_v)
                flags_t = (score_if_t >= seuil) | (force_t if force_t is not None else False)
                score_t = sorties["test"][cle][0]
                f1_val = pr.f1_selon_seuil(yv, score_if_v, force_v)
                f1_test = pr.f1_selon_seuil(yt, score_if_t, force_t)
            m = pr.metriques_decision(yt, flags_t)
            b = brut[cle]
            b["valeurs"]["precision"].append(m["precision"])
            b["valeurs"]["rappel"].append(m["rappel"])
            b["valeurs"]["f1"].append(m["f1"])
            b["valeurs"]["rocAuc"].append(roc_auc_score(yt, score_t))
            b["valeurs"]["prAuc"].append(average_precision_score(yt, score_t))
            b["valeurs"]["fpPour1000"].append(m["fp_pour_1000"])
            b["matrice"].append([m["tp"], m["fp"], m["fn"], m["tn"]])
            b["seuils"].append(seuil)
            b["pr"].append(_interp_pr(yt, score_t))
            fpr, tpr, _ = roc_curve(yt, score_t)
            b["roc"].append(np.interp(GRILLE_FPR, fpr, tpr))
            b["y_test"].append(yt)
            b["score_test"].append(score_t)
            b["f1_val"].append(f1_val)
            b["f1_test"].append(f1_test)
            types = sorties["test"]["types"]
            b["par_type"].append({t: (float(flags_t[types == t].mean()), int((types == t).sum()))
                                  for t in ds.TYPES_ANOMALIES})
            # Faux positifs par cause, et résultats avant / pendant décembre (13e mois du jeu de test).
            normales = ~yt.astype(bool)
            causes = jeu.test.loc[normales & flags_t, "evenement_normal"].fillna("autre").value_counts()
            b["fp_causes"].append({k: int(causes.get(k, 0)) for k in ("treizieme_mois", "prime",
                                                                      "iban_change_legitime", "autre")})
            avant_decembre = jeu.test["mois_index"].to_numpy() < 18
            b["periodes"].append({"oct_nov": pr.metriques_decision(yt[avant_decembre], flags_t[avant_decembre]),
                                  "dec": pr.metriques_decision(yt[~avant_decembre], flags_t[~avant_decembre])})
    return {"brut": brut, "infos": infos}


def resumer(calcul: Dict) -> Dict:
    """Résultats synthétiques (JSON) à partir des calculs bruts."""
    brut, infos = calcul["brut"], calcul["infos"]
    configurations = {}
    for cle, nom in CONFIGS.items():
        b = brut[cle]
        mat = np.asarray(b["matrice"], dtype=float)
        configurations[cle] = {
            "nom": nom,
            "metriques": {m: _stat(b["valeurs"][m]) for m in METRIQUES},
            "seuil": {**_stat(b["seuils"]), "parGraine": [round(s, 3) for s in b["seuils"]]},
            "matriceConfusionMoyenne": {"vraisPositifs": float(mat[:, 0].mean()), "fauxPositifs": float(mat[:, 1].mean()),
                                        "fauxNegatifs": float(mat[:, 2].mean()), "vraisNegatifs": float(mat[:, 3].mean())},
            "rappelParType": [
                {"type": t, "libelle": ds.TYPES_ANOMALIES[t],
                 "rappel": _stat([x[t][0] for x in b["par_type"]]),
                 "nbTestMoyen": float(np.mean([x[t][1] for x in b["par_type"]]))}
                for t in ds.TYPES_ANOMALIES],
            "fauxPositifsParCause": {k: float(np.mean([x[k] for x in b["fp_causes"]]))
                                     for k in b["fp_causes"][0]},
            "parPeriode": {p: {k: _stat([x[p][k] for x in b["periodes"]])
                               for k in ("precision", "rappel", "f1", "fp_pour_1000")}
                           for p in ("oct_nov", "dec")},
        }
    prevalence = float(np.mean([y.mean() for y in brut[RETENU]["y_test"]]))
    return {
        "protocole": {
            "graines": list(pr.SEEDS), "decoupage": {"entrainement": [1, 12], "validation": [13, 15], "test": [16, 18]},
            "choixSeuil": "maximisation du F1 sur la validation", "configurationRetenue": RETENU,
            "prevalenceTest": prevalence,
        },
        "modele": {
            "algorithme": "Isolation Forest + règle IBAN modifié", "nbArbres": N_ESTIMATORS, "contamination": 0.01,
            "variables": FEATURES, "nbEntrainement": float(np.mean(infos["nbEntrainement"])),
            "tempsEntrainementS": _stat(infos["tempsEntrainementS"]),
            "tempsPredictionMsPour1000": _stat(infos["tempsPredictionMsPour1000"]),
        },
        "configurations": configurations,
    }


def exporter(calcul: Dict, res: Dict) -> Dict:
    """Section ``evaluation`` de resultats.json (relue par l'application à l'étape C)."""
    brut = calcul["brut"]
    pr_moyenne = np.mean(brut[RETENU]["pr"], axis=0)
    sortie = dict(res)
    sortie["seuilEnService"] = SEUIL_EFFECTIF
    sortie["seuilConfigure"] = SCORE_THRESHOLD
    sortie["courbePrecisionRappel"] = {
        "rappel": [round(float(x), 4) for x in GRILLE_RAPPEL[::4]],
        "precision": [round(float(x), 4) for x in pr_moyenne[::4]],
    }
    sortie["configurationRetenue"] = RETENU
    return sortie


if __name__ == "__main__":
    from . import livrables_a

    t0 = time.perf_counter()
    calcul = calculer()
    res = resumer(calcul)
    print(f"Calcul terminé en {time.perf_counter() - t0:.0f} s")
    rp.maj_resultats("evaluation", exporter(calcul, res))
    livrables_a.produire(calcul, res)
    rp.ecrire_reproduction()
    print("Rapport assemblé :", rp.assembler_rapport())
    for cle, c in res["configurations"].items():
        m = c["metriques"]
        print(f"{c['nom']}\n   " + "  ".join(f"{k}={v['moyenne']:.3f}±{v['ecartType']:.3f}" for k, v in m.items()))
