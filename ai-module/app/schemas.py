"""Schémas Pydantic pour les échanges avec le module IA."""
from typing import List, Optional

from pydantic import BaseModel


class TransactionIn(BaseModel):
    transactionId: int
    employeId: str
    iban: str
    montant: float


class DetectRequest(BaseModel):
    transactions: List[TransactionIn]


class AnomalyOut(BaseModel):
    transactionId: int
    scoreRisque: float
    niveauRisque: str
    explication: Optional[str] = ""
