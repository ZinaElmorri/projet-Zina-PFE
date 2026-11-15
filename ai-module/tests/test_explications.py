"""Tests des explications enrichies : type d'anomalie probable et recommandation."""
from fastapi.testclient import TestClient

from app.main import app
from app.montant_suspect.features import build_reasons, compute_features, recommandation, type_probable

client = TestClient(app)
HISTORIQUE = [2000.0] * 6


def _fiche(i, montant, iban="FR7630006000011234567890189", change=False, historique=HISTORIQUE, salarie=None):
    return {"ficheId": i, "salarieId": salarie or f"E{i}", "clientId": "S", "montant": montant, "iban": iban,
            "ibanChange": change, "historique": historique}


def _type(*fiches):
    df = compute_features(list(fiches))
    return [type_probable(df.iloc[i]) for i in range(len(df))]


def test_types_probables_des_principaux_cas():
    assert _type(_fiche(1, 0.0)) == ["Montant nul ou n\u00e9gatif"]
    assert _type(_fiche(1, 7400.0, change=True)) == ["IBAN modifi\u00e9 et montant \u00e9lev\u00e9"]
    assert _type(_fiche(1, 2000.0, change=True)) == ["IBAN modifi\u00e9"]
    assert _type(_fiche(1, 6400.0)) == ["Montant tr\u00e8s sup\u00e9rieur \u00e0 l'habituel"]
    assert _type(_fiche(1, 200000.0)) == ["Erreur de virgule probable"]
    assert _type(_fiche(1, 2900.0)) == ["Hausse inhabituelle"]
    assert _type(_fiche(1, 2000.0, historique=[])) == ["Salari\u00e9 sans historique"]


def test_doublon_et_iban_partage_se_distinguent():
    doublon = _type(_fiche(1, 2000.0, salarie="A"), _fiche(2, 2000.0, salarie="A"))
    assert doublon == ["Doublon probable"] * 2
    partage = _type(_fiche(1, 2000.0, salarie="A"), _fiche(2, 2010.0, salarie="B"))
    assert partage == ["IBAN partag\u00e9"] * 2
    # un IBAN modifié qui est aussi celui d'un collègue est d'abord un IBAN partagé
    assert _type(_fiche(1, 2000.0, change=True, salarie="A"), _fiche(2, 2010.0, salarie="B"))[0] == "IBAN partag\u00e9"


def test_raisons_lisibles_pour_les_nouveaux_cas():
    df = compute_features([_fiche(1, 0.0), _fiche(2, 2000.0, historique=[], salarie="Z")])
    assert "Montant nul ou n\u00e9gatif" in build_reasons(df.iloc[0])
    assert any("Aucun historique" in r for r in build_reasons(df.iloc[1]))


def test_chaque_type_a_une_recommandation():
    assert "justificatif" in recommandation("IBAN modifi\u00e9")
    assert recommandation("type inconnu")  # repli


def test_detect_renvoie_type_et_recommandation_sans_casser_le_contrat():
    payload = {"paiements": [
        {"ficheId": 1, "salarieId": "12", "clientId": "3", "montant": 7500.0, "iban": "FR7630006000011234567890189",
         "ibanChange": True, "historique": [2010.5, 1998.0, 2025.3, 2000.0, 2012.8, 2005.1]},
        {"ficheId": 2, "salarieId": "13", "clientId": "3", "montant": 2005.0, "iban": "FR7612345678901234567890123",
         "ibanChange": False, "historique": [2000.0] * 6},
    ]}
    resultats = {r["ficheId"]: r for r in client.post("/anomalies/detect", json=payload).json()["resultats"]}
    assert resultats[1]["isAnomaly"] is True
    assert resultats[1]["typeProbable"] == "IBAN modifi\u00e9 et montant \u00e9lev\u00e9"
    assert resultats[1]["recommandation"]
    # champs historiques toujours présents
    assert {"ficheId", "score", "isAnomaly", "raisons"} <= set(resultats[1])
    if not resultats[2]["isAnomaly"]:
        assert resultats[2]["typeProbable"] is None
