"""Tests du jeu de données de référence du banc d'essai (benchmark/dataset.py)."""
import pytest

pytest.importorskip("faker")

import numpy as np  # noqa: E402

from app.montant_suspect.config import FEATURES  # noqa: E402
from app.montant_suspect.features import compute_features  # noqa: E402
from benchmark import dataset as ds  # noqa: E402


@pytest.fixture(scope="module")
def reference():
    return ds.generer_jeu_de_donnees(seed=ds.SEED_REFERENCE)


def test_iban_valides_et_detection_d_un_iban_corrompu():
    rng = np.random.default_rng(1)
    ibans = [ds.generer_iban(rng) for _ in range(200)]
    assert all(ds.iban_valide(i) for i in ibans)
    corrompu = ibans[0][:-1] + str((int(ibans[0][-1]) + 1) % 10)
    assert not ds.iban_valide(corrompu)


def test_jeu_de_reference_volumetrie_et_anomalies(reference):
    assert 3000 <= reference.loc[reference["label"] == 0, "salarie_id"].nunique() <= 5000
    assert reference["societe_id"].nunique() == 9
    assert reference["mois_index"].nunique() == ds.N_MOIS
    assert 0.015 <= reference["label"].mean() <= 0.025
    assert set(reference["type_anomalie"].dropna()) == set(ds.TYPES_ANOMALIES)
    assert reference["iban"].map(ds.iban_valide).all()


def test_decoupage_chronologique_sans_fuite_et_tous_types_dans_le_test(reference):
    parts = ds.decouper(reference)
    assert sum(len(p) for p in parts.values()) == len(reference)
    assert parts["entrainement"]["mois_index"].max() < parts["validation"]["mois_index"].min()
    assert parts["validation"]["mois_index"].max() < parts["test"]["mois_index"].min()
    assert set(parts["test"]["type_anomalie"].dropna()) == set(ds.TYPES_ANOMALIES)


def test_generation_deterministe_selon_la_graine():
    petit = dict(tailles=(40, 25), n_mois=8)
    a = ds.Simulateur(seed=5, **petit).executer()
    b = ds.Simulateur(seed=5, **petit).executer()
    c = ds.Simulateur(seed=6, **petit).executer()
    assert a.drop(columns="historique").equals(b.drop(columns="historique"))
    assert not a["montant"].equals(c["montant"])


def test_features_du_modele_calculables_sur_un_mois(reference):
    mois = reference[reference["mois_index"] == 16]
    df = compute_features(ds.vers_paiements(mois))
    assert len(df) == len(mois)
    assert not df[FEATURES].isna().any().any()


def test_scenario_de_soutenance_12_anomalies_connues():
    sc = ds.construire_scenario()
    assert len(sc["fichier"]) == 1100
    assert sc["fichier"]["societe_id"].nunique() == 1
    assert len(sc["attendues"]) == 12
    assert sc["attendues"]["type_anomalie"].nunique() == 9
    assert (sc["fichier"]["label"].sum()) == 12
    # six mois de virements corrects précèdent le fichier à contrôler
    assert sc["historique"]["mois_index"].nunique() == 6
