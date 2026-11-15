| Approche | Précision | Rappel | F1 | PR-AUC | Faux positifs / 1 000 fiches | Entraînement (s) | Prédiction (ms / 1 000 fiches) |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Règles métier seules | 0,20 ± 0,01 | 0,48 ± 0,02 | 0,29 ± 0,01 | 0,11 ± 0,00 | 37,6 ± 1,0 | 0,0 | 6 |
| Isolation Forest, décision actuelle | 0,47 ± 0,09 | 0,38 ± 0,03 | 0,42 ± 0,05 | 0,41 ± 0,04 | 8,9 ± 3,1 | 3,0 | 49 |
| Isolation Forest, seuil validé | 0,15 ± 0,05 | 0,57 ± 0,09 | 0,23 ± 0,06 | 0,41 ± 0,05 | 70,8 ± 21,2 | 3,0 | 49 |
| Local Outlier Factor | 0,07 ± 0,04 | 0,17 ± 0,07 | 0,10 ± 0,05 | 0,06 ± 0,02 | 49,4 ± 17,7 | 6,7 | 128 |
| One-Class SVM | 0,57 ± 0,12 | 0,47 ± 0,05 | 0,51 ± 0,06 | 0,45 ± 0,03 | 7,5 ± 2,7 | 0,3 | 30 |
| Hybride : règles + Isolation Forest | 0,21 ± 0,01 | 0,56 ± 0,05 | 0,31 ± 0,02 | 0,15 ± 0,01 | 41,3 ± 5,2 | 3,0 | 54 |
| IF + indicateur de décembre | 0,10 ± 0,02 | 0,58 ± 0,09 | 0,16 ± 0,04 | 0,33 ± 0,05 | 129,0 ± 79,1 | 2,8 | 24 |
| IF + écart relatif à la société | 0,40 ± 0,16 | 0,54 ± 0,04 | 0,44 ± 0,10 | 0,51 ± 0,03 | 19,5 ± 10,2 | 3,1 | 23 |
| IF à variables retenues | 0,68 ± 0,28 | 0,61 ± 0,08 | 0,58 ± 0,17 | 0,62 ± 0,03 | 20,5 ± 35,3 | 3,1 | 26 |
| Hybride optimisé (règles + IF à variables retenues) | 0,88 ± 0,08 | 0,66 ± 0,03 | 0,75 ± 0,02 | 0,71 ± 0,02 | 2,0 ± 1,7 | 3,1 | 32 |

*Comparaison des approches sur le jeu de test (moyenne ± écart-type sur 5 graines ; seuils choisis sur la validation).*
