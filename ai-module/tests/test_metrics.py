"""Tests du protocole d'évaluation et de l'endpoint /model/metrics."""
import time

from fastapi.testclient import TestClient

from app.main import app
from app.montant_suspect.evaluation import evaluer
from app.montant_suspect.features import compute_features
from app.montant_suspect.model import MontantSuspectModel
from app.montant_suspect.synthetic import generate, generate_with_types

client = TestClient(app)


def test_generate_reste_compatible_et_type_les_anomalies():
    paiements, labels = generate(seed=3)
    _, labels2, types = generate_with_types(seed=3)
    assert labels == labels2
    assert all((t is None) == (lbl == 0) for t, lbl in zip(types, labels))


def test_regle_iban_modifie_signale_un_montant_normal():
    paiements, _ = generate(seed=5, anomaly_rate=0.0)
    paiements[0]["ibanChange"] = True
    modele = MontantSuspectModel()
    modele.load_or_train()
    df = compute_features(paiements)
    _, flags_if = modele.score_isolation_forest(df)
    scores, flags = modele.score(df)
    assert bool(flags[0]) is True
    assert scores[0] >= 0.6
    assert int(flags.sum()) >= int(flags_if.sum())


def test_evaluation_contient_toutes_les_metriques():
    modele = MontantSuspectModel()
    modele.load_or_train()
    rapport = evaluer(modele, modele.source, modele.nb_entrainement, modele.temps_entrainement)

    noms = [m["cle"] for m in rapport["modeles"]]
    assert noms[0] == "en_service" and {"regle", "lof", "ocsvm"} <= set(noms)
    for m in rapport["modeles"]:
        for cle in ("accuracy", "balancedAccuracy", "precision", "rappel", "f1", "rocAuc", "prAuc"):
            assert 0.0 <= m["metriques"][cle]["moyenne"] <= 1.0
        c = m["matriceConfusion"]
        assert sum(c.values()) == rapport["protocole"]["nbTirages"] * rapport["protocole"]["paiementsParTirage"]
    en_service = rapport["modeles"][0]
    assert en_service["metriques"]["rappel"]["moyenne"] >= 0.9


def test_endpoint_metrics_finit_par_renvoyer_le_rapport():
    r = client.get("/model/metrics")
    for _ in range(60):
        if r.status_code == 200:
            break
        assert r.status_code == 202
        time.sleep(0.5)
        r = client.get("/model/metrics")
    assert r.status_code == 200
    body = r.json()
    assert body["modelVersion"] == client.get("/model/health").json()["modelVersion"]
    assert len(body["modeles"]) >= 4
