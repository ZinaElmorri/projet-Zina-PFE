## Reproduction

Depuis le dossier `ai-module/` (environnement virtuel avec `pip install -r benchmark/requirements-benchmark.txt`) :

```
python -m benchmark.describe_dataset          # étape 0 : jeu de données, scénario de soutenance
python -m benchmark.evaluate_full             # étape A : évaluation du modèle (5 graines, ~1 min)
```
