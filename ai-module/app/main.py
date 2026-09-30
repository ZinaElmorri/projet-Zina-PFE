"""Point d'entrée du service IA (FastAPI)."""
from typing import List

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .anomaly_detector import detecter
from .montant_suspect.router import router as montant_suspect_router
from .schemas import AnomalyOut, DetectRequest

app = FastAPI(
    title="Module IA — Détection d'anomalies SEPA",
    version="0.1.0",
    description="Attribue un score de risque à chaque virement SEPA.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://localhost:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# Module additif : détection des montants suspects (Isolation Forest).
app.include_router(montant_suspect_router)


@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/detect", response_model=List[AnomalyOut])
def detect(request: DetectRequest):
    """Analyse une liste de transactions et retourne les scores de risque."""
    return detecter(request.transactions)
