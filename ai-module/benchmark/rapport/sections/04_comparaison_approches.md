## 4. Comparaison des approches (étape B)

Approches comparées avec le protocole de la section 3 : règles métier seules (IBAN, montant, doublon, « 2 fois la moyenne »),
Isolation Forest actuel, Local Outlier Factor, One-Class SVM (entraîné sur un échantillon de 10 000 fiches), hybride règles + IA,
et trois variantes de l'Isolation Forest (indicateur de décembre, écart relatif à la société, variables retenues sur la validation).

### 4.1 Comparaison globale

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

**Légende proposée** : Comparaison des approches de détection sur le jeu de test (moyenne ± écart-type sur 5 graines).

**Interprétation** : Les règles métier seules atteignent un F1 de 0,29 (rappel 0,48) mais signalent 38 fiches normales pour 1 000, car la règle « 2 fois la moyenne » alerte aussi les 13e mois. L'Isolation Forest actuel obtient un F1 de 0,42 avec sa décision actuelle et une PR-AUC de 0,41. Le One-Class SVM, entraîné en une fraction de seconde, fait aussi bien ou mieux (F1 0,51, PR-AUC 0,45) : le choix de l'algorithme n'est donc pas le facteur déterminant, et le Local Outlier Factor, presque au niveau du hasard (ROC-AUC 0,58), est à écarter. Ce qui fait la différence, ce sont les variables et la combinaison avec les règles : l'hybride optimisé atteint un F1 de 0,75 ± 0,02, une PR-AUC de 0,71 et seulement 2 faux positifs pour 1 000 fiches. Les temps de calcul sont négligeables pour toutes les approches (entraînement de quelques secondes, prédiction en quelques dizaines de millisecondes pour 1 000 fiches).

![fig_comparaison_f1_prauc](rapport/figures/fig_comparaison_f1_prauc.png)

**Légende proposée** : F1 et PR-AUC de chaque approche sur le jeu de test (barres d'erreur : écart-type sur 5 graines).

**Interprétation** : Le classement par F1 et par PR-AUC est cohérent. En bas, les règles seules et le Local Outlier Factor restent sous 0,3 de F1. Les trois variantes de l'Isolation Forest qui ajoutent une information utile (écart relatif à la société, variables retenues, combinaison avec les règles) progressent à chaque étape, de 0,23 à 0,44, puis 0,58, puis 0,75. L'indicateur de décembre, lui, dégrade le résultat (0,16 contre 0,23). L'écart-type est élevé pour les variantes sans règles (jusqu'à 0,17 sur le F1), car leur seuil dépend du tirage ; l'hybride est le plus stable (0,02) parce que les règles détectent avec certitude une partie des anomalies. Les barres d'erreur invitent à ne pas sur-interpréter les écarts entre variantes proches.

### 4.2 Variantes pour le 13e mois

**Légende proposée** : Analyse des variantes « indicateur de décembre » et « écart relatif à la société ».

**Interprétation** : Ajouter à l'Isolation Forest un indicateur de décembre, pour absorber le 13e mois, n'améliore pas le résultat : le F1 passe de 0,23 à 0,16 et les faux positifs de 71 à 129 pour 1 000 fiches. Avec un seul mois de décembre dans l'entraînement, l'indicateur isole ce mois dans son ensemble au lieu de décrire un comportement propre aux sociétés concernées : sur la graine de référence, 99 % des 13e mois sont signalés (contre 70 % sans l'indicateur) et les fiches de décembre sans 13e mois le sont aussi plus souvent (3 % contre 0,7 %). À l'inverse, rapporter l'écart à l'historique de chaque salarié à la médiane de sa société pour le mois traite l'effet collectif sans connaître le calendrier : un 13e mois ou une augmentation générale décale toute la société, pas un salarié isolé. Cette variable porte le F1 à 0,44 et la PR-AUC à 0,51. Elle présente en outre l'avantage de ne nécessiter aucune donnée supplémentaire : elle se calcule sur le lot contrôlé. La leçon méthodologique est qu'une variable qui exprime la cause (effet collectif) vaut mieux qu'une variable qui désigne la période.

### 4.3 Ablation des variables

| Variable retirée | F1 (test) | PR-AUC (test) | Variation (test) | PR-AUC (validation) | Variation (validation) |
| :--- | ---: | ---: | ---: | ---: | ---: |
| Aucune (modèle complet) | 0,23 | 0,41 | — | 0,59 | — |
| Montant | 0,31 | 0,51 | +0,10 | 0,69 | +0,10 |
| Écart à la moyenne du salarié | 0,33 | 0,38 | -0,03 | 0,49 | -0,10 |
| Z-score dans la société | 0,26 | 0,46 | +0,05 | 0,69 | +0,11 |
| Variation sur le mois précédent | 0,21 | 0,36 | -0,05 | 0,48 | -0,10 |
| IBAN modifié | 0,20 | 0,35 | -0,06 | 0,54 | -0,05 |
| Paiements sur le même IBAN | 0,29 | 0,38 | -0,03 | 0,49 | -0,09 |

*Ablation des variables de l'Isolation Forest : performance après retrait d'une variable (moyenne sur 5 graines).*

**Légende proposée** : Ablation : performance de l'Isolation Forest quand on retire une variable (moyenne sur 5 graines).

**Interprétation** : Les variables les plus utiles sont IBAN modifié, variation sur le mois précédent, paiements sur le même IBAN : les retirer fait baisser la PR-AUC de 0,06 à 0,03 sur le test. À l'inverse, retirer montant ou z-score dans la société améliore la PR-AUC (par exemple 0,10 de variation en retirant montant). Le montant brut est en effet peu informatif lorsque les salaires vont de 1 500 à plus de 10 000 € : sur la graine de référence, 52 des 92 fiches normales signalées par la décision actuelle sont celles de dirigeants, qui ne représentent pourtant que 2,7 % des fiches normales. Le z-score dans la société souffre du même défaut. Les mêmes variables ressortent sur la validation, où la sélection a été faite : c'est ce qui permet de les écarter sans avoir regardé le test. Ce résultat dépend de l'hétérogénéité des profils du jeu synthétique, qu'il faudra confirmer sur des données réelles.

![fig_ablation_variables](rapport/figures/fig_ablation_variables.png)

**Légende proposée** : Variation de la PR-AUC lorsqu'une variable est retirée de l'Isolation Forest (test et validation).

**Interprétation** : Une barre négative signale une variable utile (sa suppression dégrade la PR-AUC), une barre positive une variable qui gêne le modèle. Les barres du test et de la validation vont dans le même sens pour les six variables, ce qui valide la sélection faite sur la validation seule. Le montant brut et le z-score dans la société sont les deux variables nuisibles. L'indicateur d'IBAN modifié, la variation sur le mois précédent et le nombre de paiements sur le même IBAN sont parmi les plus utiles, l'écart à la moyenne du salarié étant à un niveau comparable. Cette lecture a servi à construire la variante à variables retenues.

### 4.4 Valeur ajoutée de l'IA

| Type d'anomalie | Anomalies (test) | Règles seules | IA seule | Règles et IA | Non détectées |
| :--- | ---: | ---: | ---: | ---: | ---: |
| Montant multiplié par 3 à 10 | 26 | 0,6 | 0,0 | 25,0 | 0,0 |
| Erreur de virgule (×100 ou ÷100) | 24 | 0,0 | 0,8 | 13,0 | 10,2 |
| Hausse subtile (+25 à +60 %) | 28 | 0,4 | 0,6 | 0,4 | 27,0 |
| Montant nul ou négatif | 30 | 20,8 | 0,0 | 9,6 | 0,0 |
| Doublon (salarié payé deux fois) | 27 | 25,6 | 0,0 | 1,6 | 0,0 |
| IBAN modifié et montant élevé | 28 | 0,0 | 9,4 | 18,4 | 0,0 |
| IBAN partagé entre deux salariés | 23 | 0,0 | 11,6 | 1,4 | 9,8 |
| Salarié fantôme (sans historique) | 29 | 0,0 | 0,0 | 0,0 | 28,6 |
| Salarié sorti encore payé | 27 | 0,0 | 0,0 | 0,0 | 26,6 |
| Total | 241 | 47,4 | 22,4 | 69,4 | 102,2 |

*Valeur ajoutée de l'IA : anomalies du jeu de test détectées par les règles seules, par l'IA seule (décision actuelle), par les deux, ou par aucune (nombre moyen de fiches par graine).*

**Légende proposée** : Valeur ajoutée de l'IA : anomalies détectées par les règles seules, par l'IA seule, par les deux, ou par aucune.

**Interprétation** : Sur environ 241 anomalies par jeu de test, 22 ne sont vues que par l'IA, soit 16 % des anomalies détectées, contre 47 vues seulement par les règles et 69 par les deux. Les types où l'IA apporte le plus sont IBAN partagé entre deux salariés, IBAN modifié et montant élevé, erreur de virgule (×100 ou ÷100). Les règles, elles, sont irremplaçables pour doublon (salarié payé deux fois) et montant nul ou négatif, qu'un modèle statistique distingue mal. En contrepartie, les règles produisent 423 fausses alertes que l'IA ne partage pas (les 13e mois), contre 72 pour l'IA seule. 102 anomalies échappent aux deux, principalement les salariés fantômes, les salariés sortis encore payés et les hausses subtiles : ces types exigent l'historique de présence du salarié. Les deux approches sont donc complémentaires, ce qui justifie l'hybride.

![fig_valeur_ajoutee_regles_ia](rapport/figures/fig_valeur_ajoutee_regles_ia.png)

**Légende proposée** : Répartition des anomalies de chaque type selon le détecteur (règles, IA, les deux, aucun).

**Interprétation** : Le diagramme montre pour chaque type la part des anomalies vues par les deux détecteurs, par les règles seules, par l'IA seule ou par aucun. Les erreurs de montant importantes sont retrouvées par les deux approches, ce qui les rend robustes. Les anomalies qui ne contredisent aucune règle simple (IBAN partagé, erreur de virgule vers le bas) relèvent surtout de l'IA, ou ne sont détectées par personne. La partie grise, importante pour la hausse subtile, le salarié fantôme et le salarié sorti, désigne les anomalies non couvertes par le dispositif actuel et oriente les évolutions futures. Aucun des deux détecteurs ne suffit seul.

### 4.5 Détection par type et par approche

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

**Légende proposée** : Taux de détection par type d'anomalie pour les principales approches (jeu de test).

**Interprétation** : Ce tableau détaille, type par type, ce que chaque approche détecte. Les règles métier atteignent un rappel complet sur les doublons et les montants nuls ou négatifs, ce que l'Isolation Forest actuel ne fait pas (6 % et 28 %). À l'inverse, l'Isolation Forest et le One-Class SVM sont meilleurs sur les IBAN modifiés et les IBAN partagés, que les règles ne voient pas. Retirer le montant brut des variables rend la hausse subtile détectable (63 à 75 % contre 3 %), et l'hybride optimisé atteint 100 % sur quatre autres types. Il reste un point faible : l'IBAN partagé n'est détecté qu'à un tiers, loin du One-Class SVM, ce qui appelle une règle explicite sur les IBAN communs à plusieurs salariés. Le salarié fantôme et le salarié sorti encore payé restent à 0 % pour toutes les approches.

![fig_rappel_par_type_approches](rapport/figures/fig_rappel_par_type_approches.png)

**Légende proposée** : Rappel par type d'anomalie (en %) pour les principales approches, jeu de test.

**Interprétation** : La carte de chaleur résume les complémentarités : les colonnes de règles sont foncées pour les doublons et les montants nuls, celles des modèles statistiques pour les IBAN. La colonne de l'hybride optimisé est la plus foncée dans l'ensemble, sauf pour l'IBAN partagé. Seules les deux dernières lignes (salarié fantôme, salarié sorti) restent entièrement claires, et la hausse subtile ne devient détectable qu'après retrait du montant brut. Les deux types sans détection appellent des informations nouvelles (historique de présence, rapprochement avec le registre du personnel) plutôt qu'un algorithme différent.

### 4.6 Justification du choix du modèle

**Légende proposée** : Justification du choix du modèle.

**Interprétation** : Nous recommandons l'hybride optimisé (règles métier et Isolation Forest sur cinq variables) : sur le test, il détecte 66 % des anomalies avec une précision de 88 %, contre 38 % et 47 % pour la décision actuelle, et réduit les faux positifs de 9 à 2 pour 1 000 fiches. Ce choix n'a utilisé que la validation (sélection des variables et du seuil) ; il est aussi le meilleur sur la validation. Il ne dépend pas d'un algorithme sophistiqué : le One-Class SVM est une alternative crédible, et le gain provient surtout des règles métier, de la suppression des variables nuisibles et de la normalisation par société. L'IA apporte une valeur propre : 22 anomalies par jeu de test, soit 16 % des anomalies détectées, échappent aux règles, alors que les règles seules produisent 423 fausses alertes que l'IA ne partage pas (contre 72 pour l'IA seule). Réserves : les données sont synthétiques et les anomalies injectées par des règles connues ; les performances réelles devront être mesurées lors d'un pilote. Ce modèle est préparé comme version candidate (voir `benchmark/candidat_v2/`) et n'est pas activé : le modèle en service reste inchangé.
