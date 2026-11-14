"""Tests du protocole d'évaluation du banc d'essai (benchmark/protocole.py)."""
import pytest

pytest.importorskip("faker")

import numpy as np  # noqa: E402

from app.montant_suspect.config import FEATURES  # noqa: E402
from benchmark import dataset as ds  # noqa: E402
from benchmark import protocole as pr  # noqa: E402


def test_matrice_et_metriques():
    y = np.array([1, 1, 1, 0, 0, 0, 0, 0, 0, 0])
    flags = np.array([1, 1, 0, 1, 0, 0, 0, 0, 0, 0])
    assert pr.matrice(y, flags) == (2, 1, 1, 6)
    m = pr.metriques_decision(y, flags)
    assert m["precision"] == pytest.approx(2 / 3)
    assert m["rappel"] == pytest.approx(2 / 3)
    assert m["fp_pour_1000"] == pytest.approx(100.0)


def test_le_seuil_est_choisi_uniquement_sur_les_donnees_de_validation():
    y_val = np.array([0] * 90 + [1] * 10)
    score_val = np.concatenate([np.linspace(0.0, 0.4, 90), np.linspace(0.6, 0.9, 10)])
    seuil = pr.choisir_seuil(y_val, score_val)
    assert 0.4 < seuil <= 0.6  # sépare parfaitement les deux classes
    # Un autre jeu (test) ne peut pas modifier le choix : la fonction ne reçoit que la validation.
    assert pr.choisir_seuil(y_val, score_val) == seuil


def test_la_decision_en_service_equivaut_a_un_seuil_de_0_5():
    """Prédiction native -1 de l'Isolation Forest <=> score >= 0,5 : le seuil configuré à 0,6 est sans effet."""
    sim = ds.Simulateur(tailles=(120, 80), seed=3, n_mois=8)
    df = sim.executer()
    jeu = pr._features_par_mois(df)
    modele = pr.entrainer_modele_en_service(jeu, seed=3)
    scores_service, flags_service = modele.score(jeu)
    score_if, _ = modele.score_isolation_forest(jeu)
    attendu = (score_if >= 0.5) | (jeu["iban_change_recent"].to_numpy() == 1)
    assert (attendu == flags_service).all()
    assert set(FEATURES) <= set(jeu.columns)
