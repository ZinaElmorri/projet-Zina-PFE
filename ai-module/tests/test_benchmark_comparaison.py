"""Tests de l'étape B : règles métier du banc d'essai et version candidate (non activée)."""
import os

import pandas as pd
import pytest

pytest.importorskip("faker")

import joblib  # noqa: E402

from benchmark import modeles as md  # noqa: E402
from benchmark.exporter_candidat import DOSSIER  # noqa: E402


def _fiches(lignes):
    df = pd.DataFrame(lignes)
    df["ficheId"] = range(1, len(df) + 1)
    return df


def test_regles_metier_signalent_les_deux_fiches_d_un_doublon_et_les_montants_invalides():
    df = _fiches([
        {"mois_index": 1, "salarie_id": "A", "iban": "X1", "montant": 2000.0, "iban_valide": True,
         "ecart_moyenne_salarie": 1.0, "ecart_relatif_societe": 1.0},
        {"mois_index": 1, "salarie_id": "A", "iban": "X1", "montant": 2000.0, "iban_valide": True,
         "ecart_moyenne_salarie": 1.0, "ecart_relatif_societe": 1.0},
        {"mois_index": 1, "salarie_id": "B", "iban": "X2", "montant": -5.0, "iban_valide": True,
         "ecart_moyenne_salarie": 1.0, "ecart_relatif_societe": 1.0},
        {"mois_index": 1, "salarie_id": "C", "iban": "X3", "montant": 4100.0, "iban_valide": False,
         "ecart_moyenne_salarie": 2.1, "ecart_relatif_societe": 1.0},
        {"mois_index": 1, "salarie_id": "D", "iban": "X4", "montant": 2000.0, "iban_valide": True,
         "ecart_moyenne_salarie": 1.0, "ecart_relatif_societe": 1.0},
    ])
    r = md.regles_metier(df)
    assert list(r["doublon"]) == [True, True, False, False, False]
    assert list(r["montant_non_positif"]) == [False, False, True, False, False]
    assert list(r["iban_invalide"]) == [False, False, False, True, False]
    assert list(r["declenchee"]) == [True, True, True, True, False]
    # la variante relative n'alerte pas sur un effet collectif (13e mois de toute la société)
    assert md.regles_metier(df, relatif=True)["montant_double_moyenne"].sum() == 0
    assert r["montant_double_moyenne"].sum() == 1


def test_la_version_candidate_est_preparee_mais_non_activee():
    chemin = os.path.join(DOSSIER, "hybride_optimise_v2.joblib")
    if not os.path.exists(chemin):
        pytest.skip("candidat non généré (python -m benchmark.exporter_candidat)")
    candidat = joblib.load(chemin)
    assert candidat["statut"] == "candidat, non active"
    assert "montant" not in candidat["variables"]
    assert "ecart_relatif_societe" in candidat["variables"]
    # le modèle en service n'est pas touché : le candidat vit hors de app/
    assert "benchmark" in os.path.abspath(chemin)
