# Scénario de soutenance (données)

Fichiers générés par `python -m benchmark.describe_dataset` (graine 2026, déterministe).

| Fichier | Contenu |
|---|---|
| `historique_6_mois.csv` | 6 mois de virements corrects (avril à septembre 2025) de la société SOC-01 |
| `fichier_paie_mois.csv` | Le fichier de paie d'octobre 2025 à contrôler : **1 100 fiches** |
| `anomalies_attendues.csv` | Les **12 fiches anormales** connues, avec type et description attendue |
| `cas_normaux_atypiques.csv` | Fiches normales qui ressemblent à des anomalies (primes, changements d'IBAN légitimes) : un faux positif sur ces lignes est discutable, pas une erreur du jeu |

Les 12 anomalies : 1 montant multiplié, 2 erreurs de virgule (×100 et ÷100), 1 hausse subtile, 1 montant nul ou négatif,
1 IBAN modifié avec montant élevé, 1 salarié fantôme, 1 salarié sorti encore payé, 1 doublon (2 fiches) et 1 IBAN partagé (2 fiches).

L'import dans l'application et le mode d'emploi complet sont décrits à l'étape C.
