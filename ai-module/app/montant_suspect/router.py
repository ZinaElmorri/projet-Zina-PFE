"""Routeur FastAPI du module « montants suspects » (additif, non intrusif)."""
from typing import Optional

from fastapi import APIRouter

from .config import SCORE_THRESHOLD
from .features import build_reasons, compute_features
from .model import MontantSuspectModel
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
    return HealthMS(status="UP", modelVersion=_model.version)


@router.post("/anomalies/detect", response_model=DetectResponseMS)
def detect(request: DetectRequestMS) -> DetectResponseMS:
    if not request.paiements:
        return DetectResponseMS(modelVersion=_model.version or "", resultats=[])

    df = compute_features([p.model_dump() for p in request.paiements])
    scores, flags = _model.score(df)

    resultats = []
    for i in range(len(df)):
        row = df.iloc[i]
        score = round(float(scores[i]), 3)
        is_anomaly = bool(flags[i])
        resultats.append(ResultatMS(
            ficheId=int(row["ficheId"]),
            score=score,
            isAnomaly=is_anomaly,
            raisons=build_reasons(row) if is_anomaly else [],
        ))
    return DetectResponseMS(modelVersion=_model.version or "", resultats=resultats)


@router.post("/model/train", response_model=TrainResponse)
def train(request: Optional[TrainRequestMS] = None) -> TrainResponse:
    if request and request.paiements:
        version = _model.train(compute_features([p.model_dump() for p in request.paiements]))
        message = "Modèle réentraîné sur les données fournies"
    else:
        version = _model.train_synthetic()
        message = "Modèle réentraîné sur des données synthétiques"
    return TrainResponse(modelVersion=version, message=message)
