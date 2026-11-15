## Reproduction

Depuis le dossier `ai-module/` (environnement virtuel avec `pip install -r benchmark/requirements-benchmark.txt`) :

```
python -m benchmark.describe_dataset          # étape 0 : jeu de données, scénario de soutenance
python -m benchmark.evaluate_full             # étape A : évaluation du modèle (5 graines, ~1 min)
python -m benchmark.compare                   # étape B : comparaison des approches (~8 min ; --cache pour les livrables seuls)
python -m benchmark.exporter_candidat         # étape B : version candidate (non activée)
python -m benchmark.importer_scenario         # étape C : import du scénario de soutenance (application lancée)
node benchmark/capturer_ecrans.mjs <dossier>  # étape C : captures d'écran (puppeteer-core + Edge)
python -m benchmark.livrables_c               # étape C : textes, tableau et section du rapport
```
