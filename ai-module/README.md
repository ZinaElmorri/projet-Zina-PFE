# Module IA — Détection des montants suspects et reconnaissance faciale

Service Python/FastAPI appelé uniquement par le backend Spring Boot.

## Prérequis
- Python 3.11+

## Installation & lancement

```bash
python -m venv .venv
.venv\Scripts\activate        # Windows
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Documentation interactive : `http://localhost:8000/docs`

## Endpoints

| Méthode | Chemin               | Description |
|---------|----------------------|-------------|
| GET     | `/health`            | Disponibilité du service |
| GET     | `/model/health`      | Version du modèle, source et taille du jeu d'entraînement |
| POST    | `/anomalies/detect`  | Score de suspicion (0-1), décision et raisons pour chaque paiement |
| POST    | `/model/train`       | Réentraînement (corps vide = données synthétiques, sinon paiements réels) |
| GET     | `/model/metrics`     | Rapport d'évaluation du modèle en service (202 pendant le calcul) |
| POST    | `/api/face/encode`, `/api/face/verify` | Reconnaissance faciale (YuNet + SFace) |

## Modèle de détection

- **Isolation Forest** (non supervisé, 200 arbres, contamination 1 %) sur 6 variables :
  montant, écart à la moyenne du salarié (6 mois), z-score dans le client, variation vs mois précédent,
  IBAN modifié, nombre de paiements sur le même IBAN.
- **Règle complémentaire « IBAN modifié »** : un changement d'IBAN seul (montant normal) n'isole pas le point
  dans les arbres (rappel mesuré : 0 %) ; il est donc toujours signalé pour vérification.
- Décision : anomalie si prédiction Isolation Forest = -1, score ≥ seuil (0,6) ou IBAN modifié.
- Raisons lisibles produites pour chaque alerte (`features.build_reasons`).

Paramètres (variables d'environnement) : `AI_SCORE_THRESHOLD`, `AI_CONTAMINATION`, `AI_N_ESTIMATORS`,
`AI_HISTORY_MONTHS`, `AI_REGLE_IBAN_CHANGE`.

## Évaluation

Après chaque entraînement, le modèle est évalué en arrière-plan et le rapport est écrit dans
`app/montant_suspect/models/metrics.json` (exposé par `/model/metrics` et l'écran « Intelligence artificielle »).

- 5 jeux de test synthétiques étiquetés de 1 000 paiements (3 % d'anomalies de 3 types), indépendants du jeu d'entraînement ;
- métriques en moyenne ± écart-type : accuracy, accuracy équilibrée, précision, rappel, F1, ROC-AUC, PR-AUC ;
- matrice de confusion cumulée et rappel par type d'anomalie ;
- comparaison : Isolation Forest seul, règle simple (montant > 2× moyenne), Local Outlier Factor, One-Class SVM.

```bash
python -m scripts.compare_models --json rapport.json
```

L'accuracy seule est trompeuse (classes déséquilibrées) : un modèle qui ne signale rien obtient ~97 %.
Les indicateurs pertinents sont le rappel, la précision, le F1 et le PR-AUC.

## Tests

```bash
python -m pytest -q
```
