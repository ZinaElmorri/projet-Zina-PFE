"""Tests du calcul des features."""
from app.montant_suspect.config import FEATURES
from app.montant_suspect.features import build_reasons, compute_features


def test_ecart_moyenne_sans_historique_vaut_1():
    df = compute_features([
        {"ficheId": 1, "clientId": "3", "montant": 5000.0, "iban": "FR7612", "ibanChange": False, "historique": []},
    ])
    assert df.iloc[0]["ecart_moyenne_salarie"] == 1.0
    for col in FEATURES:
        assert col in df.columns


def test_ecart_moyenne_avec_historique():
    df = compute_features([
        {"ficheId": 1, "clientId": "3", "montant": 6000.0, "iban": "FR7612",
         "ibanChange": False, "historique": [2000.0, 2000.0, 2000.0, 2000.0, 2000.0, 2000.0]},
    ])
    # 6000 / 2000 = 3
    assert abs(df.iloc[0]["ecart_moyenne_salarie"] - 3.0) < 1e-6
    assert abs(df.iloc[0]["variation_mois_precedent"] - 200.0) < 1e-6


def test_iban_partage_et_zscore_client():
    df = compute_features([
        {"ficheId": 1, "clientId": "1", "montant": 2000.0, "iban": "FR7600001", "ibanChange": False, "historique": [2000.0]},
        {"ficheId": 2, "clientId": "1", "montant": 2000.0, "iban": "FR7600001", "ibanChange": False, "historique": [2000.0]},
        {"ficheId": 3, "clientId": "1", "montant": 20000.0, "iban": "FR7699999", "ibanChange": True, "historique": [2000.0]},
    ])
    partages = df.set_index("ficheId")["nb_paiements_meme_iban"]
    assert partages.loc[1] == 2
    assert partages.loc[3] == 1
    # Le très gros montant a un z-score client élevé.
    zscore = df.set_index("ficheId")["zscore_client"]
    assert abs(zscore.loc[3]) >= 1.0


def test_build_reasons_montant_eleve():
    df = compute_features([
        {"ficheId": 1, "clientId": "3", "montant": 7400.0, "iban": "FR7612", "ibanChange": True,
         "historique": [2000.0, 2000.0, 2000.0, 2000.0, 2000.0, 2000.0]},
    ])
    raisons = build_reasons(df.iloc[0])
    assert any("moyenne" in r for r in raisons)
    assert any("IBAN" in r for r in raisons)
