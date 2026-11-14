| Type d'anomalie | Fiches (test) | Décision actuelle (seuil effectif 0,5) | Isolation Forest, seuil validé |
| :--- | ---: | ---: | ---: |
| Montant multiplié par 3 à 10 | 26 | 98 ± 3 % | 100 ± 0 % |
| Erreur de virgule (×100 ou ÷100) | 24 | 57 ± 7 % | 100 ± 0 % |
| Hausse subtile (+25 à +60 %) | 28 | 3 ± 3 % | 26 ± 25 % |
| Montant nul ou négatif | 30 | 28 ± 17 % | 100 ± 0 % |
| Doublon (salarié payé deux fois) | 27 | 6 ± 9 % | 28 ± 37 % |
| IBAN modifié et montant élevé | 28 | 100 ± 0 % | 100 ± 0 % |
| IBAN partagé entre deux salariés | 23 | 57 ± 4 % | 66 ± 18 % |
| Salarié fantôme (sans historique) | 29 | 0 ± 0 % | 0 ± 0 % |
| Salarié sorti encore payé | 27 | 0 ± 0 % | 1 ± 2 % |

*Taux de détection (rappel) par type d'anomalie sur le jeu de test, moyenne ± écart-type sur 5 graines.*
