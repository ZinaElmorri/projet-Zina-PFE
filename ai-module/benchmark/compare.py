"""Étape B : comparaison des approches de détection (livrables du rapport).

Usage (depuis ai-module/) :  python -m benchmark.compare [--cache]

Même protocole que l'étape A : 5 graines, entraînement mois 1–12, seuil choisi sur la validation (mois 13–15),
résultats sur le test (mois 16–18). ``--cache`` réutilise les calculs bruts du dernier passage
(benchmark/data/compare_brut.pkl) pour ne régénérer que les livrables.
"""
from __future__ import annotations

import argparse
import os
import pickle
import time
from typing import Dict

import numpy as np
from sklearn.metrics import average_precision_score, roc_auc_score

from app.montant_suspect.config import FEATURES

from . import dataset as ds
from . import modeles as md
from . import protocole as pr
from . import reporting as rp

APPROCHES = {
    "regles": "Règles métier seules",
    "if_actuel": "Isolation Forest, décision actuelle",
    "if_valide": "Isolation Forest, seuil validé",
    "lof": "Local Outlier Factor",
    "ocsvm": "One-Class SVM",
    "hybride": "Hybride : règles + Isolation Forest",
    "if_calendrier": "Isolation Forest + indicateur de décembre",
    "if_collectif": "Isolation Forest + écart relatif à la société",
    "if_optimise": "Isolation Forest, variables retenues sur la validation",
    "hybride_optimise": "Hybride optimisé : règles + IF à variables retenues",
}
CACHE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "data", "compare_brut.pkl")
METRIQUES = ("precision", "rappel", "f1", "prAuc", "rocAuc", "fpPour1000")
SEUIL_EFFECTIF = 0.5


def _stat(valeurs) -> Dict[str, float]:
    v = np.asarray(valeurs, dtype=float)
    return {"moyenne": float(v.mean()), "ecartType": float(v.std(ddof=0))}


def _grille(score_val: np.ndarray) -> np.ndarray:
    if score_val.min() >= 0 and score_val.max() <= 1:
        return pr.GRILLE_SEUILS
    return np.unique(np.quantile(score_val, np.linspace(0.5, 1.0, 801)))


def _seuil_valide(yv, score_v, forces_v=None) -> float:
    return pr.choisir_seuil(yv, score_v, forces_v, _grille(score_v))


def _evaluer(yt, types_t, flags, score, seuil, f1_val, t_fit, t_pred_s, n_test) -> Dict:
    m = pr.metriques_decision(yt, flags)
    return {
        "precision": m["precision"], "rappel": m["rappel"], "f1": m["f1"],
        "prAuc": float(average_precision_score(yt, score)), "rocAuc": float(roc_auc_score(yt, score)),
        "fpPour1000": m["fp_pour_1000"], "seuil": seuil, "f1Validation": f1_val,
        "tempsEntrainementS": t_fit, "tempsPredictionMsPour1000": t_pred_s / n_test * 1000 * 1000,
        "parType": {t: float(flags[types_t == t].mean()) for t in ds.TYPES_ANOMALIES},
        "flags": flags.copy(),
    }


def _venn(yt, types_t, regles, ia) -> Dict:
    y, r, a = yt.astype(bool), regles.astype(bool), ia.astype(bool)
    par_type = {}
    for t in ds.TYPES_ANOMALIES:
        s = types_t == t
        par_type[t] = {"regles_seules": int((s & r & ~a).sum()), "ia_seule": int((s & ~r & a).sum()),
                       "les_deux": int((s & r & a).sum()), "aucune": int((s & ~r & ~a).sum())}
    return {"parType": par_type,
            "fauxPositifs": {"regles_seules": int((~y & r & ~a).sum()), "ia_seule": int((~y & ~r & a).sum()),
                             "les_deux": int((~y & r & a).sum())}}


def calculer() -> Dict:
    brut = {k: [] for k in APPROCHES}
    venn, venn_valide, ablation, selections = [], [], [], []
    for seed in pr.SEEDS:
        t0 = time.perf_counter()
        jeu = pr.preparer(seed)
        tr, va, te = jeu.entrainement, jeu.validation, jeu.test
        yv, yt, types_t = va["label"].to_numpy(), te["label"].to_numpy(), te["type_anomalie"].to_numpy()
        n = len(te)
        print(f"  graine {seed} : données prêtes ({time.perf_counter() - t0:.0f} s)")

        # Règles métier
        t0 = time.perf_counter()
        regles_t = md.regles_metier(te)["declenchee"].to_numpy()
        t_regles = time.perf_counter() - t0
        regles_v = md.regles_metier(va)["declenchee"].to_numpy()
        nb_regles_t = md.regles_metier(te).drop(columns="declenchee").sum(axis=1).to_numpy()
        f1_val_regles = pr.metriques_decision(yv, regles_v)["f1"]
        brut["regles"].append(_evaluer(yt, types_t, regles_t, nb_regles_t, None, f1_val_regles, 0.0, t_regles, n))

        # Isolation Forest de production (décision actuelle) et variante au seuil validé
        t0 = time.perf_counter()
        prod = pr.entrainer_modele_en_service(tr, seed)
        t_fit_if = time.perf_counter() - t0
        t0 = time.perf_counter()
        score_service, flags_service = prod.score(te)
        t_pred_if = time.perf_counter() - t0
        score_if_v, _ = prod.score_isolation_forest(va)
        score_if_t, _ = prod.score_isolation_forest(te)
        flags_service_v = prod.score(va)[1]
        brut["if_actuel"].append(_evaluer(yt, types_t, np.asarray(flags_service), np.asarray(score_service),
                                          SEUIL_EFFECTIF, pr.metriques_decision(yv, flags_service_v)["f1"],
                                          t_fit_if, t_pred_if, n))
        seuil = _seuil_valide(yv, score_if_v)
        f1v = pr.metriques_decision(yv, score_if_v >= seuil)["f1"]
        brut["if_valide"].append(_evaluer(yt, types_t, score_if_t >= seuil, score_if_t, seuil, f1v, t_fit_if, t_pred_if, n))
        flags_if_valide = score_if_t >= seuil

        # LOF et One-Class SVM (seuil choisi sur la validation)
        for cle, classe in (("lof", md.LocalOutlierFactorModele), ("ocsvm", md.OneClassSvmModele)):
            t0 = time.perf_counter()
            modele = classe(seed).fit(tr)
            t_fit = time.perf_counter() - t0
            s_v = modele.score(va)
            t0 = time.perf_counter()
            s_t = modele.score(te)
            t_pred = time.perf_counter() - t0
            seuil = _seuil_valide(yv, s_v)
            brut[cle].append(_evaluer(yt, types_t, s_t >= seuil, s_t, seuil,
                                      pr.metriques_decision(yv, s_v >= seuil)["f1"], t_fit, t_pred, n))
            print(f"    {cle} : entraînement {t_fit:.1f} s, prédiction {t_pred:.1f} s")

        # Hybride : règles métier + Isolation Forest (seuil choisi sur la validation de l'ensemble)
        seuil = _seuil_valide(yv, score_if_v, regles_v)
        flags_h = regles_t | (score_if_t >= seuil)
        brut["hybride"].append(_evaluer(yt, types_t, flags_h, np.where(regles_t, 2.0, score_if_t), seuil,
                                        pr.metriques_decision(yv, regles_v | (score_if_v >= seuil))["f1"],
                                        t_fit_if, t_pred_if + t_regles, n))

        # Variante calendrier : indicateur de décembre ajouté aux variables
        t0 = time.perf_counter()
        cal = md.IsolationForestGenerique(md.FEATURES_CALENDRIER, seed).fit(tr)
        t_fit_cal = time.perf_counter() - t0
        s_cv = cal.score(va)
        t0 = time.perf_counter()
        s_ct = cal.score(te)
        t_pred_cal = time.perf_counter() - t0
        seuil = _seuil_valide(yv, s_cv)
        brut["if_calendrier"].append(_evaluer(yt, types_t, s_ct >= seuil, s_ct, seuil,
                                              pr.metriques_decision(yv, s_cv >= seuil)["f1"], t_fit_cal, t_pred_cal, n))

        # Variante « effet collectif » : écart à l'historique rapporté à la médiane de la société pour le mois
        t0 = time.perf_counter()
        col = md.IsolationForestGenerique(md.FEATURES_COLLECTIF, seed).fit(tr)
        t_fit_col = time.perf_counter() - t0
        s_colv = col.score(va)
        t0 = time.perf_counter()
        s_colt = col.score(te)
        t_pred_col = time.perf_counter() - t0
        seuil = _seuil_valide(yv, s_colv)
        brut["if_collectif"].append(_evaluer(yt, types_t, s_colt >= seuil, s_colt, seuil,
                                             pr.metriques_decision(yv, s_colv >= seuil)["f1"], t_fit_col, t_pred_col, n))

        # Variables retenues par ablation sur la VALIDATION seule (retrait de toute variable qui améliore la PR-AUC)
        prauc_val = {}
        for f in md.FEATURES_COLLECTIF + [None]:
            g = md.IsolationForestGenerique([x for x in md.FEATURES_COLLECTIF if x != f], seed).fit(tr)
            prauc_val[f] = float(average_precision_score(yv, g.score(va)))
        retenues = [f for f in md.FEATURES_COLLECTIF if prauc_val[f] <= prauc_val[None] + 1e-9]
        if len(retenues) < 3:
            retenues = list(md.FEATURES_COLLECTIF)
        selections.append(retenues)
        t0 = time.perf_counter()
        opt = md.IsolationForestGenerique(retenues, seed).fit(tr)
        t_fit_opt = time.perf_counter() - t0
        s_ov = opt.score(va)
        t0 = time.perf_counter()
        s_ot = opt.score(te)
        t_pred_opt = time.perf_counter() - t0
        seuil = _seuil_valide(yv, s_ov)
        brut["if_optimise"].append(_evaluer(yt, types_t, s_ot >= seuil, s_ot, seuil,
                                            pr.metriques_decision(yv, s_ov >= seuil)["f1"], t_fit_opt, t_pred_opt, n))
        regles_rel_v = md.regles_metier(va, relatif=True)["declenchee"].to_numpy()
        regles_rel_t = md.regles_metier(te, relatif=True)["declenchee"].to_numpy()
        seuil = _seuil_valide(yv, s_ov, regles_rel_v)
        brut["hybride_optimise"].append(_evaluer(
            yt, types_t, regles_rel_t | (s_ot >= seuil), np.where(regles_rel_t, 2.0, s_ot), seuil,
            pr.metriques_decision(yv, regles_rel_v | (s_ov >= seuil))["f1"], t_fit_opt, t_pred_opt + t_regles, n))

        # Valeur ajoutée de l'IA : décision actuelle d'une part, seuil validé d'autre part, face aux règles
        venn.append(_venn(yt, types_t, regles_t, np.asarray(flags_service)))
        venn_valide.append(_venn(yt, types_t, regles_t, flags_if_valide))

        # Ablation : Isolation Forest sans l'une des variables (seuil choisi sur la validation)
        lignes = {}
        for f in FEATURES + [None]:
            variables = [x for x in FEATURES if x != f]
            g = md.IsolationForestGenerique(variables, seed).fit(tr)
            s_v, s_t = g.score(va), g.score(te)
            seuil = _seuil_valide(yv, s_v)
            m = pr.metriques_decision(yt, s_t >= seuil)
            lignes[f or "_complet"] = {"f1": m["f1"], "rappel": m["rappel"], "precision": m["precision"],
                                       "prAuc": float(average_precision_score(yt, s_t)),
                                       "prAucValidation": float(average_precision_score(yv, s_v)),
                                       "parType": {t: float((s_t >= seuil)[types_t == t].mean()) for t in ds.TYPES_ANOMALIES}}
        ablation.append(lignes)
    return {"brut": brut, "venn": venn, "vennValide": venn_valide, "ablation": ablation, "selections": selections}


def resumer(calcul: Dict) -> Dict:
    brut = calcul["brut"]
    approches = {}
    for cle, nom in APPROCHES.items():
        runs = brut[cle]
        approches[cle] = {
            "nom": nom,
            "metriques": {m: _stat([r[m] for r in runs]) for m in METRIQUES},
            "f1Validation": _stat([r["f1Validation"] for r in runs]),
            "seuil": None if runs[0]["seuil"] is None else _stat([r["seuil"] for r in runs]),
            "tempsEntrainementS": _stat([r["tempsEntrainementS"] for r in runs]),
            "tempsPredictionMsPour1000": _stat([r["tempsPredictionMsPour1000"] for r in runs]),
            "rappelParType": {t: _stat([r["parType"][t] for r in runs]) for t in ds.TYPES_ANOMALIES},
        }

    def agreger_venn(liste):
        sortie = {"parType": {}, "fauxPositifs": {}}
        for t in ds.TYPES_ANOMALIES:
            sortie["parType"][t] = {k: float(np.mean([v["parType"][t][k] for v in liste]))
                                    for k in ("regles_seules", "ia_seule", "les_deux", "aucune")}
        sortie["fauxPositifs"] = {k: float(np.mean([v["fauxPositifs"][k] for v in liste]))
                                  for k in ("regles_seules", "ia_seule", "les_deux")}
        return sortie

    ablation = {}
    complet = calcul["ablation"]
    for f in FEATURES + ["_complet"]:
        ablation[f] = {m: _stat([a[f][m] for a in complet]) for m in ("f1", "rappel", "precision", "prAuc", "prAucValidation")}
        ablation[f]["rappelParType"] = {t: _stat([a[f]["parType"][t] for a in complet]) for t in ds.TYPES_ANOMALIES}
    retenues = {f: sum(f in s for s in calcul["selections"]) for f in md.FEATURES_COLLECTIF}
    return {"approches": approches, "valeurAjoutee": agreger_venn(calcul["venn"]),
            "valeurAjouteeSeuilValide": agreger_venn(calcul["vennValide"]), "ablation": ablation,
            "variablesRetenues": {"nbGraines": len(calcul["selections"]), "parVariable": retenues}}


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", action="store_true", help="Réutilise les calculs bruts du dernier passage")
    args = parser.parse_args()
    from . import livrables_b

    t0 = time.perf_counter()
    if args.cache and os.path.exists(CACHE):
        with open(CACHE, "rb") as f:
            calcul = pickle.load(f)
    else:
        calcul = calculer()
        os.makedirs(os.path.dirname(CACHE), exist_ok=True)
        with open(CACHE, "wb") as f:
            pickle.dump(calcul, f)
    res = resumer(calcul)
    print(f"Calcul terminé en {time.perf_counter() - t0:.0f} s")
    rp.maj_resultats("comparaison", res)
    livrables_b.produire(calcul, res)
    rp.ecrire_reproduction()
    print("Rapport assemblé :", rp.assembler_rapport())
    for cle, a in res["approches"].items():
        m = a["metriques"]
        print(f"{a['nom']:<42}" + "  ".join(f"{k}={v['moyenne']:.3f}±{v['ecartType']:.3f}" for k, v in m.items())
              + f"  F1val={a['f1Validation']['moyenne']:.3f}"
              + f"  fit={a['tempsEntrainementS']['moyenne']:.1f}s pred={a['tempsPredictionMsPour1000']['moyenne']:.1f}ms/1000")
    print("Ablation (F1, PR-AUC test, PR-AUC validation) :")
    for f, a in res["ablation"].items():
        print(f"  sans {f:<28} F1={a['f1']['moyenne']:.3f} PR-AUC={a['prAuc']['moyenne']:.3f} "
              f"PR-AUC val={a['prAucValidation']['moyenne']:.3f}")
    print("Variables retenues (nb de graines) :", res["variablesRetenues"]["parVariable"])
