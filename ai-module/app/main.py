"""Point d'entrée du service IA (FastAPI)."""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from .face.router import router as face_router
from .montant_suspect.router import router as montant_suspect_router

app = FastAPI(
    title="Module IA — Détection d'anomalies SEPA",
    version="0.2.0",
    description="Détection des montants suspects (Isolation Forest) et reconnaissance faciale.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["http://localhost:8080", "http://localhost:5173"],
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(montant_suspect_router)
app.include_router(face_router)


@app.get("/health")
def health():
    return {"status": "ok"}
