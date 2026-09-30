"""Tests de l'endpoint /anomalies/detect."""
from fastapi.testclient import TestClient

from app.main import app

client = TestClient(app)


def test_health_module():
    r = client.get("/model/health")
    assert r.status_code == 200
    body = r.json()
    assert body["status"] == "UP"
    assert body["modelVersion"]


def test_detect_signale_montant_suspect():
    payload = {
        "periode": "2026-08",
        "paiements": [
            {"ficheId": 101, "salarieId": "12", "clientId": "3", "montant": 7500.0,
             "iban": "FR7630006000011234567890189", "ibanChange": True,
             "historique": [2010.5, 1998.0, 2025.3, 2000.0, 2012.8, 2005.1]},
            {"ficheId": 102, "salarieId": "13", "clientId": "3", "montant": 2005.0,
             "iban": "FR7612345678901234567890123", "ibanChange": False,
             "historique": [2000.0, 2000.0, 2000.0, 2000.0, 2000.0, 2000.0]},
        ],
    }
    r = client.post("/anomalies/detect", json=payload)
    assert r.status_code == 200
    body = r.json()
    assert body["modelVersion"]
    resultats = {x["ficheId"]: x for x in body["resultats"]}
    # Le paiement à 7500 € (habituellement 2000 €) doit être signalé avec des raisons.
    assert resultats[101]["isAnomaly"] is True
    assert len(resultats[101]["raisons"]) >= 1


def test_detect_liste_vide():
    r = client.post("/anomalies/detect", json={"paiements": []})
    assert r.status_code == 200
    assert r.json()["resultats"] == []
