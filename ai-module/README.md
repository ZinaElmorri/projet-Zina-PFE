# Module IA — Détection d'anomalies

Service Python/FastAPI qui attribue un **score de risque** à chaque virement SEPA à l'aide de Scikit-Learn (Isolation Forest, Local Outlier Factor) et de règles statistiques.

## Prérequis
- Python 3.11+

## Installation & lancement

```bash
python -m venv .venv
.venv\Scripts\activate        # Windows
# source .venv/bin/activate   # Linux/macOS
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Documentation interactive : `http://localhost:8000/docs`

## Endpoints

| Méthode | Chemin        | Description                                  |
|---------|---------------|----------------------------------------------|
| GET     | `/health`     | Vérification de disponibilité                |
| POST    | `/api/detect` | Analyse des transactions → scores de risque  |

### Exemple de requête

```json
POST /api/detect
{
  "transactions": [
    {"transactionId": 1, "employeId": "E001", "iban": "FR7630006000011234567890189", "montant": 2500.0},
    {"transactionId": 2, "employeId": "E002", "iban": "FR7630006000011234567890189", "montant": 98000.0}
  ]
}
```

### Réponse

```json
[
  {"transactionId": 1, "scoreRisque": 0.12, "niveauRisque": "FAIBLE", "explication": "aucune anomalie détectée"},
  {"transactionId": 2, "scoreRisque": 0.91, "niveauRisque": "CRITIQUE", "explication": "montant statistiquement anormal"}
]
```

## Méthodes de détection

- **Isolation Forest** — anomalies globales sur les montants
- **Local Outlier Factor** — anomalies de densité locale
- **Z-score** — écart statistique par rapport à la moyenne
- **Doublons** — même IBAN + même montant
- **IBAN atypiques** — longueur hors norme ISO 13616

## Structure

```
ai-module/
├── app/
│   ├── main.py              # API FastAPI
│   ├── schemas.py           # Modèles Pydantic
│   └── anomaly_detector.py  # Moteur de détection
├── requirements.txt
└── Dockerfile
```
