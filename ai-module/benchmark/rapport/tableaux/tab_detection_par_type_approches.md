| Type d'anomalie | Règles métier seules | Isolation Forest, décision actuelle | One-Class SVM | Hybride : règles + Isolation Forest | IF à variables retenues | Hybride optimisé (règles + IF à variables retenues) |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: |
| Montant multiplié par 3 à 10 | 100 % | 98 % | 88 % | 100 % | 100 % | 100 % |
| Erreur de virgule (×100 ou ÷100) | 54 % | 57 % | 67 % | 70 % | 100 % | 100 % |
| Hausse subtile (+25 à +60 %) | 3 % | 3 % | 1 % | 6 % | 75 % | 63 % |
| Montant nul ou négatif | 100 % | 28 % | 66 % | 100 % | 100 % | 100 % |
| Doublon (salarié payé deux fois) | 100 % | 6 % | 79 % | 100 % | 26 % | 100 % |
| IBAN modifié et montant élevé | 66 % | 100 % | 43 % | 88 % | 100 % | 100 % |
| IBAN partagé entre deux salariés | 7 % | 57 % | 92 % | 37 % | 49 % | 32 % |
| Salarié fantôme (sans historique) | 0 % | 0 % | 0 % | 0 % | 0 % | 0 % |
| Salarié sorti encore payé | 0 % | 0 % | 0 % | 0 % | 0 % | 0 % |

*Taux de détection (rappel) par type d'anomalie et par approche, jeu de test, moyenne sur 5 graines.*
