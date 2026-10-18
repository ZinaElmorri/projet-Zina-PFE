"""Routeur FastAPI du module « montants suspects »."""
from typing import Optional

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from .features import build_reasons, compute_features
from .model import SOURCE_REEL, SOURCE_SYNTHETIQUE, MontantSuspectModel
from .schemas import (
    DetectRequestMS,
    DetectResponseMS,
    HealthMS,
    ResultatMS,
    TrainRequestMS,
    TrainResponse,
)

router = APIRouter(tags=["Montants suspects"])

_model = MontantSuspectModel()
_model.load_or_train()


@router.get("/model/health", response_model=HealthMS)
def model_health() -> HealthMS:
    return HealthMS(status="UP", modelVersion=_model.version, source=_model.source,
                    nbEntrainement=_model.nb_entrainement)


@router.get("/model/metrics")
def model_metrics():
    """Rapport d'évaluation du modèle en service (202 tant qu'il est en cours de calcul)."""
    rapport = _model.metriques()
    if rapport is None:
        return JSONResponse(status_code=202, content={
            "etat": "EN_COURS", "modelVersion": _model.version,
            "message": "Évaluation du modèle en cours, réessayez dans quelques secondes."})
    return rapport


@router.post("/anomalies/detect", response_model=DetectResponseMS)
def detect(request: DetectRequestMS) -> DetectResponseMS:
    if not request.paiements:
        return DetectResponseMS(modelVersion=_model.version or "", resultats=[])

    df = compute_features([p.model_dump() for p in request.paiements])
    scores, flags = _model.score(df)

    resultats = []
    for i in range(len(df)):
        row = df.iloc[i]
        is_anomaly = bool(flags[i])
        resultats.append(ResultatMS(
            ficheId=int(row["ficheId"]),
            score=round(float(scores[i]), 3),
            isAnomaly=is_anomaly,
            raisons=build_reasons(row) if is_anomaly else [],
        ))
    return DetectResponseMS(modelVersion=_model.version or "", resultats=resultats)


@router.post("/model/train", response_model=TrainResponse)
def train(request: Optional[TrainRequestMS] = None) -> TrainResponse:
    if request and request.paiements:
        version = _model.train(compute_features([p.model_dump() for p in request.paiements]), SOURCE_REEL)
        message = f"Modèle réentraîné sur {len(request.paiements)} paiements réels"
    else:
        version = _model.train_synthetic()
        message = "Modèle réentraîné sur des données synthétiques"
    return TrainResponse(modelVersion=version, message=message)


__all__ = ["router", "SOURCE_SYNTHETIQUE", "SOURCE_REEL"]
