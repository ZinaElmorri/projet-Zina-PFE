"""Routeur FastAPI de reconnaissance faciale."""
from typing import List

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from .service import COSINE_THRESHOLD, FaceError, decode_image, engine

router = APIRouter(prefix="/api/face", tags=["Reconnaissance faciale"])


class EncodeRequest(BaseModel):
    image: str = Field(..., description="Image JPEG/PNG en base64 (data URL acceptée)")


class EncodeResponse(BaseModel):
    encoding: List[float]


class VerifyRequest(BaseModel):
    image: str
    reference: List[float]


class VerifyResponse(BaseModel):
    match: bool
    score: float
    seuil: float


def _encode(image: str) -> List[float]:
    try:
        return engine.encode(decode_image(image))
    except FaceError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc


@router.get("/health")
def face_health():
    return {"status": "UP" if engine.available() else "DOWN", "seuil": COSINE_THRESHOLD}


@router.post("/encode", response_model=EncodeResponse)
def encode(request: EncodeRequest) -> EncodeResponse:
    return EncodeResponse(encoding=_encode(request.image))


@router.post("/verify", response_model=VerifyResponse)
def verify(request: VerifyRequest) -> VerifyResponse:
    encoding = _encode(request.image)
    try:
        score = engine.similarity(encoding, request.reference)
    except FaceError as exc:
        raise HTTPException(status_code=422, detail=str(exc)) from exc
    return VerifyResponse(match=score >= COSINE_THRESHOLD, score=round(score, 4), seuil=COSINE_THRESHOLD)
