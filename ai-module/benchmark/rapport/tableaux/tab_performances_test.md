| Configuration | Précision | Rappel | F1 | ROC-AUC | PR-AUC | Faux positifs / 1 000 fiches | Seuil |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Décision actuelle en production (score ≥ 0,5 ou IBAN modifié) | 0,47 ± 0,09 | 0,38 ± 0,03 | 0,42 ± 0,05 | 0,84 ± 0,01 | 0,41 ± 0,04 | 8,9 ± 3,1 | 0,50 |
| Isolation Forest seul (seuil choisi sur la validation) | 0,15 ± 0,05 | 0,57 ± 0,09 | 0,23 ± 0,06 | 0,84 ± 0,01 | 0,41 ± 0,05 | 70,8 ± 21,2 | 0,34 ± 0,07 |
| Isolation Forest + règle IBAN modifié (seuil choisi sur la validation) | 0,16 ± 0,06 | 0,57 ± 0,09 | 0,24 ± 0,07 | 0,84 ± 0,01 | 0,33 ± 0,04 | 68,1 ± 22,4 | 0,34 ± 0,07 |

*Performances sur le jeu de test (mois 16 à 18), moyenne ± écart-type sur 5 graines. Référence au hasard : PR-AUC = taux d'anomalies (2,0 %), ROC-AUC = 0,50.*
