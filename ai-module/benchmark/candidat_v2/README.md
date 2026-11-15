# Version candidate « hybride optimisé » (non activée)

Contenu généré par `python -m benchmark.exporter_candidat` :

- `hybride_optimise_v2.joblib` : Isolation Forest (200 arbres) et standardiseur appris sur les mois 1 à 12 du jeu de référence,
  avec les métadonnées (variables, seuil, règles, version) ;
- `metadonnees.json` : les mêmes métadonnées lisibles, avec les performances mesurées.

**Statut : candidat, non activé.** Le modèle en service (`app/montant_suspect/models/iforest.pkl`) et l'API
(`/anomalies/detect`, `/model/train`, `/model/health`) sont inchangés.

## Ce qui change par rapport au modèle en service

| | En service | Candidat |
|---|---|---|
| Variables de l'Isolation Forest | 6 (dont le montant brut et le z-score dans la société) | 5 : écart à la moyenne du salarié, variation sur le mois précédent, IBAN modifié, paiements sur le même IBAN, **écart relatif à la société** (nouvelle) |
| Décision | prédiction native ou score ≥ 0,6 ou IBAN modifié | règles métier (IBAN invalide, montant ≤ 0, doublon, écart relatif > 2) **ou** score ≥ seuil choisi sur la validation |

## Pour l'activer (travail à faire, non réalisé)

1. Ajouter la variable `ecart_relatif_societe` dans `app/montant_suspect/features.py` (écart du salarié divisé par la médiane
   de sa société dans le lot) et mettre à jour `config.FEATURES`.
2. Ajouter les règles métier à la décision dans `model.py` (`score`).
3. Charger le fichier `.joblib` à la place de `iforest.pkl`, puis mettre à jour les tests et l'évaluation.
4. Mesurer le candidat sur des données réelles avant toute mise en production.
