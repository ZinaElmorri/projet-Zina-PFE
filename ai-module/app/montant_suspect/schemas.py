"""Schémas Pydantic du contrat d'échange /anomalies/detect et /model/train."""
from typing import List, Optional

from pydantic import BaseModel, Field


class PaiementIn(BaseModel):
    ficheId: int
    salarieId: Optional[str] = None
    clientId: Optional[str] = None
    montant: float
    iban: Optional[str] = ""
    ibanChange: bool = False
    # Montants nets des derniers mois du salarié (le plus récent en dernier).
    historique: List[float] = Field(default_factory=list)


class DetectRequestMS(BaseModel):
    periode: Optional[str] = None
    paiements: List[PaiementIn] = Field(default_factory=list)


class ResultatMS(BaseModel):
    ficheId: int
    score: float
    isAnomaly: bool
    raisons: List[str] = Field(default_factory=list)


class DetectResponseMS(BaseModel):
    modelVersion: str
    resultats: List[ResultatMS]


class TrainRequestMS(BaseModel):
    paiements: List[PaiementIn] = Field(default_factory=list)


class TrainResponse(BaseModel):
    modelVersion: str
    message: str


class HealthMS(BaseModel):
    status: str
    modelVersion: Optional[str] = None
