## 3. Évaluation du modèle (étape A)

### 3.1 Protocole d'évaluation

**Légende proposée** : Protocole d'évaluation du modèle de détection.

**Interprétation** : Le jeu de données est découpé selon l'ordre chronologique des mois de paie : les mois 1 à 12 servent à entraîner le modèle, les mois 13 à 15 à choisir le seuil de décision, et les mois 16 à 18, jamais utilisés auparavant, à mesurer les performances finales. Ce découpage reproduit l'usage réel (contrôler un mois futur avec un modèle appris sur le passé) et interdit toute fuite d'information. Le modèle évalué est celui de l'application, reconstruit avec ses hyperparamètres (200 arbres, contamination de 1 %). Sa décision actuelle (prédiction native de l'Isolation Forest, ou score d'au moins 0,6, ou IBAN modifié) équivaut en pratique à « score d'au moins 0,5 ou IBAN modifié » : le seuil configuré à 0,6 est sans effet puisque la prédiction native signale déjà tout score supérieur à 0,5. Cette décision, fixée avant l'évaluation, est mesurée telle quelle, puis comparée à des variantes dont le seuil est choisi sur la validation (maximisation du F1) ; aucun paramètre n'est ajusté en regardant le test. Les mesures retenues sont la précision, le rappel, le F1, la ROC-AUC et la PR-AUC, ainsi que le nombre de faux positifs pour 1 000 fiches, qui traduit la charge de vérification de l'Expert RH. Comme les anomalies représentent environ 2 % des fiches, la ROC-AUC reste flatteuse (le grand nombre de fiches normales gonfle le taux de vrais négatifs) : la PR-AUC, dont la valeur de référence est le taux d'anomalies, est la mesure la plus pertinente. Chaque expérience est répétée sur cinq graines (jeu de données et modèle) et rapportée en moyenne ± écart-type.

### 3.2 Performances sur le jeu de test

| Configuration | Précision | Rappel | F1 | ROC-AUC | PR-AUC | Faux positifs / 1 000 fiches | Seuil |
| :--- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Décision actuelle en production (score ≥ 0,5 ou IBAN modifié) | 0,47 ± 0,09 | 0,38 ± 0,03 | 0,42 ± 0,05 | 0,84 ± 0,01 | 0,41 ± 0,04 | 8,9 ± 3,1 | 0,50 |
| Isolation Forest seul (seuil choisi sur la validation) | 0,15 ± 0,05 | 0,57 ± 0,09 | 0,23 ± 0,06 | 0,84 ± 0,01 | 0,41 ± 0,05 | 70,8 ± 21,2 | 0,34 ± 0,07 |
| Isolation Forest + règle IBAN modifié (seuil choisi sur la validation) | 0,16 ± 0,06 | 0,57 ± 0,09 | 0,24 ± 0,07 | 0,84 ± 0,01 | 0,33 ± 0,04 | 68,1 ± 22,4 | 0,34 ± 0,07 |

*Performances sur le jeu de test (mois 16 à 18), moyenne ± écart-type sur 5 graines. Référence au hasard : PR-AUC = taux d'anomalies (2,0 %), ROC-AUC = 0,50.*

**Légende proposée** : Performances du modèle sur le jeu de test (mois 16 à 18), moyenne ± écart-type sur 5 graines.

**Interprétation** : Avec sa décision actuelle (en pratique un seuil de 0,5 sur le score), le modèle en service atteint une précision de 0,47 ± 0,09, un rappel de 0,38 ± 0,03 et un F1 de 0,42 ± 0,05, soit environ 9 faux positifs pour 1 000 fiches. Sa PR-AUC de 0,41 ± 0,04 est environ 21 fois supérieure à celle d'un classement au hasard (2,0 %), ce qui prouve que le score contient une vraie information, mais elle reste loin de 1 : le modèle détecte environ 38 % des anomalies, pas toutes. Recalibrer le seuil sur la validation donne un seuil de 0,34 ± 0,07 et un rappel plus élevé (0,57 ± 0,09), mais au prix d'une précision de 0,15 ± 0,05 et de 71 faux positifs pour 1 000 fiches : le seuil appris sur des mois sans 13e mois ne se transfère pas au mois de décembre. Ajouter la règle IBAN dégrade la PR-AUC (0,33 ± 0,04 contre 0,41 ± 0,05 sans elle) car elle signale tous les changements d'IBAN légitimes, alors que le modèle repère déjà seul les changements d'IBAN accompagnés d'un montant élevé.

![fig_courbe_precision_rappel](rapport/figures/fig_courbe_precision_rappel.png)

**Légende proposée** : Courbe précision–rappel du modèle en service sur le jeu de test (trait fin : chaque graine ; trait épais : moyenne).

**Interprétation** : La courbe précision–rappel décrit le compromis offert par le score, indépendamment du seuil. Elle reste nettement au-dessus du niveau du hasard (2,0 %), avec une PR-AUC de 0,41 ± 0,04. Les anomalies les mieux classées (jusqu'à un rappel d'environ 0,14) sont retrouvées avec une précision supérieure à 0,95, puis la précision décroît à mesure que l'on accepte de signaler davantage de fiches, jusqu'à un long palier d'environ 0,12 pour les rappels élevés, dû au bloc des 13e mois classés parmi les fiches suspectes. Le seuil actuel (étoile) offre une précision de 0,47 pour un rappel de 0,38 ; le seuil recalibré sur la validation (losange) gagne en rappel (0,57) en perdant beaucoup de précision (0,15). La dispersion entre graines est modérée, signe que le résultat ne dépend pas d'un tirage favorable.

![fig_courbe_roc](rapport/figures/fig_courbe_roc.png)

**Légende proposée** : Courbe ROC du modèle en service sur le jeu de test (trait fin : chaque graine ; trait épais : moyenne).

**Interprétation** : La courbe ROC s'écarte nettement de la diagonale, avec une aire de 0,84 ± 0,01. Autrement dit, une fiche anormale tirée au hasard reçoit un score plus élevé qu'une fiche normale dans environ 84 % des cas. Cette mesure doit toutefois être lue avec prudence : comme les fiches normales sont environ 50 fois plus nombreuses, un taux de faux positifs de quelques pourcents correspond déjà à beaucoup de fausses alertes en valeur absolue. C'est pourquoi la courbe précision–rappel, plus sensible au déséquilibre, est la référence de ce rapport. La ROC confirme néanmoins que le score ordonne correctement les fiches.

![fig_matrice_confusion](rapport/figures/fig_matrice_confusion.png)

**Légende proposée** : Matrices de confusion moyennes sur le jeu de test (effectifs par graine et part de la ligne réelle).

**Interprétation** : À gauche, la décision actuelle signale en moyenne 92 anomalies vraies sur 241, et laisse passer 150 anomalies (faux négatifs). Les faux positifs sont peu nombreux en proportion (108 fiches sur plus de 12 000), ce qui ménage l'Expert RH. À droite, le seuil recalibré avec la règle IBAN détecte davantage d'anomalies mais produit 833 faux positifs en moyenne, soit une charge de vérification nettement supérieure. Le choix du seuil traduit donc un arbitrage entre anomalies manquées et temps de vérification, à fixer avec les utilisateurs.

### 3.3 Choix du seuil et distribution des scores

![fig_distribution_scores](rapport/figures/fig_distribution_scores.png)

**Légende proposée** : Distribution des scores de l'Isolation Forest sur le jeu de test, selon la classe réelle.

**Interprétation** : La grande majorité des fiches normales a un score faible, avec toutefois un second regroupement autour de 0,4, qui correspond aux 13e mois (ces fiches représentent l'essentiel des faux positifs au seuil recalibré). Les anomalies s'étalent sur toute la plage, avec une queue marquée vers les scores élevés. Les deux distributions se recouvrent largement entre 0,2 et 0,6 : c'est la zone où se jouent les faux positifs et les faux négatifs. Au seuil actuel (0,5), peu de fiches normales sont signalées, mais une bonne part des anomalies, dont le score reste bas, passe inaperçue. Le seuil choisi sur la validation est plus bas et capte plus d'anomalies, mais il se place avant le regroupement des 13e mois et en signale donc presque toutes les fiches. L'échelle logarithmique rend visibles ces queues, invisibles sur une échelle linéaire.

![fig_f1_en_fonction_du_seuil](rapport/figures/fig_f1_en_fonction_du_seuil.png)

**Légende proposée** : F1 en fonction du seuil de décision, sur la validation (trait plein) et sur le test (tirets).

**Interprétation** : Sur la validation, le F1 atteint son maximum (0,56) pour un seuil d'environ 0,34, valeur retenue par le protocole. La courbe de test, tracée à titre de contrôle et jamais utilisée pour choisir, culmine à un seuil plus élevé (environ 0,48, F1 de 0,43) et ne donne que 0,18 au seuil choisi sur la validation : la validation (juillet à septembre) ne contient pas de 13e mois, alors que le test (octobre à décembre) en contient un. Le décalage entre les deux courbes est donc d'origine saisonnière et non un défaut de protocole. Il montre qu'un seuil unique appris sur peu de mois n'est pas robuste, et qu'il vaut mieux corriger la cause (variable de calendrier) que recalibrer en permanence. Le seuil actuel de 0,5 donne un F1 de 0,42 sur le test, proche de ce maximum, sans l'avoir été choisi sur ces données : ce bon résultat tient à un seuil prudent, pas à un réglage fin.

### 3.4 Détection par type d'anomalie

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

**Légende proposée** : Taux de détection par type d'anomalie sur le jeu de test (rappel, moyenne ± écart-type sur 5 graines).

**Interprétation** : Le modèle détecte bien les anomalies qui déforment fortement le montant : Montant multiplié par 3 à 10, IBAN modifié et montant élevé (rappel d'au moins 80 % au seuil actuel), parce que l'écart à l'historique et le z-score dans la société deviennent extrêmes. Il détecte très mal Hausse subtile (+25 à +60 %), Doublon (salarié payé deux fois), Salarié fantôme (sans historique), Salarié sorti encore payé (rappel d'au plus 20 %). Pour la hausse subtile, la cause est de fond : une hausse de 25 à 60 % recouvre celle des primes et promotions normales, aucun modèle fondé sur le seul montant ne peut la distinguer sans faux positifs. Pour le salarié fantôme et le salarié sorti encore payé, l'information décisive (l'absence d'historique, l'absence de paiement le mois précédent) n'est pas dans les variables actuelles ; elle peut être ajoutée (la colonne correspondante est déjà fournie par le jeu de données). Le doublon demande une règle explicite sur l'identité des fiches, plus fiable que l'isolement statistique. Abaisser le seuil augmente le rappel de plusieurs types (par exemple montant nul ou négatif : 28 % puis 100 %) mais au prix de nombreux faux positifs.

![fig_rappel_par_type](rapport/figures/fig_rappel_par_type.png)

**Légende proposée** : Rappel par type d'anomalie sur le jeu de test (barres d'erreur : écart-type sur 5 graines).

**Interprétation** : Les types sont triés du moins bien au mieux détecté au seuil actuel. Le contraste est net : les erreurs qui défigurent le montant (multiplication, IBAN modifié avec montant élevé) sont presque toutes repérées, alors que le salarié fantôme, le salarié sorti encore payé, la hausse subtile et le doublon passent presque toujours inaperçus. Un seuil plus bas améliore le rappel de la plupart des types « montant » mais ne change rien aux deux types qui échappent à l'analyse des montants. Cela oriente les améliorations : des règles de cohérence pour les doublons et les montants nuls, de nouvelles variables pour les salariés fantômes et sortis, et un traitement spécifique de la saisonnalité.

### 3.5 Sensibilité à la saisonnalité et faux positifs

| Configuration | Période du test | Précision | Rappel | F1 | Faux positifs / 1 000 fiches |
| :--- | :--- | ---: | ---: | ---: | ---: |
| Décision actuelle (seuil effectif 0,5) | Octobre – novembre | 0,65 | 0,35 | 0,45 | 3,8 |
| Décision actuelle (seuil effectif 0,5) | Décembre (13e mois) | 0,34 | 0,45 | 0,38 | 19,1 |
| Isolation Forest, seuil validé | Octobre – novembre | 0,55 | 0,54 | 0,53 | 9,7 |
| Isolation Forest, seuil validé | Décembre (13e mois) | 0,07 | 0,63 | 0,12 | 193,3 |

*Performances avant et pendant décembre, où trois sociétés versent un 13e mois (moyenne sur 5 graines).*

**Légende proposée** : Performances avant et pendant décembre, mois du 13e mois de trois sociétés (moyenne sur 5 graines).

**Interprétation** : Les résultats se dégradent nettement en décembre : au seuil actuel, la précision passe de 0,65 (octobre et novembre) à 0,34 et les faux positifs pour 1 000 fiches de 3,8 à 19,1. Pour le seuil choisi sur la validation, l'écart est bien plus grand : 10 contre 193 faux positifs pour 1 000 fiches. Le 13e mois double le montant de la fiche de trois sociétés : pour le modèle, qui ne connaît pas le calendrier, c'est un montant deux fois supérieur à l'historique. Le seuil choisi sur la validation (juillet à septembre) ne pouvait pas anticiper ce phénomène. Il s'agit d'une limite réelle du modèle, qui justifie d'ajouter une variable de calendrier ou un indicateur de paiement exceptionnel déclaré.

| Configuration | Faux positifs | 13e mois | Primes | Changements d'IBAN légitimes | Autres fiches normales |
| :--- | ---: | ---: | ---: | ---: | ---: |
| Décision actuelle (seuil effectif 0,5) | 108 | 66 | 1 | 24 | 17 |
| Isolation Forest, seuil validé | 866 | 757 | 9 | 6 | 95 |

*Causes des faux positifs sur le jeu de test (nombre moyen de fiches par graine).*

**Légende proposée** : Causes des faux positifs sur le jeu de test (nombre moyen de fiches par graine).

**Interprétation** : Au seuil actuel, 61 % des faux positifs sont des 13e mois, 24 des changements d'IBAN légitimes (signalés d'office par la règle IBAN) et 17 d'autres fiches normales. Pour le seuil choisi sur la validation, les 13e mois représentent 87 % des faux positifs. Les primes ponctuelles, pourtant conçues comme des cas difficiles, génèrent très peu de fausses alertes : leur amplitude (jusqu'à +35 %) reste dans la tolérance du modèle. Presque toutes les fausses alertes proviennent donc de situations légitimes mais prévisibles que le modèle ignore, ce qui indique les améliorations les plus rentables.
