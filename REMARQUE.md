# REMARQUES DU JURY ACADÉMIQUE, CONSEILS ET PRÉPARATION À LA SOUTENANCE
### Projet de Fin d'Études — Diplôme National d'Ingénieur en Informatique (SESAME)
**Candidate :** Zina ELMORRI  
**Sujet :** *Système de gestion des flux SEPA (ISO 20022) avec détection intelligente d'anomalies pour la paie HR Access*  
**Organisme d'accueil :** Sopra HR Software (Équipe R&D Internationale)  
**Encadrants :** M. Mohamed Zied FIOUEL (Entreprise) & M. Malek BOUZAIENE (Universitaire)

---

## SOMMAIRE
1. [Évaluation générale et avis du jury académique](#1-évaluation-générale-et-avis-du-jury-académique)
2. [La question piège du jury sur l'IA (Pourquoi ~40 % et pourquoi ne pas changer de modèle ?)](#2-la-question-piège-du-jury-sur-lia)
3. [Les 8 questions clés du jury et réponses types pour la soutenance](#3-les-8-questions-clés-du-jury-et-réponses-types)
4. [Axes d'amélioration pour viser la note de 19/20](#4-axes-damélioration-pour-viser-la-note-de-1920)
5. [Conseils stratégiques pour le jour de la soutenance (Oral et Démo)](#5-conseils-stratégiques-pour-le-jour-j)

---

## 1. ÉVALUATION GÉNÉRALE ET AVIS DU JURY ACADÉMIQUE

### Note prévisionnelle actuelle du mémoire écrit : **17,5 / 20** (Mention Très Bien visée)

### Appréciation synthétique :
Le travail présenté dépasse largement un projet étudiant ordinaire. Il aborde une triple complexité industrielle :
- La rigueur des **normes bancaires internationales (ISO 20022, message pain.001, SEPA SCT)**.
- Une **architecture logicielle moderne, modulaire et hautement sécurisée** (React 18, Spring Boot 3, FastAPI, PostgreSQL, Docker Compose).
- La **Data Science appliquée à la détection d'anomalies de paie**, avec un banc d'essai expérimental exemplaire.

### Bilan par critère :
| Critère | Appréciation | Note indicative |
| :--- | :--- | :---: |
| **Rigueur méthodologique** | Démarche Scrum adaptée à un projet individuel, backlog de 24 US, traçabilité | **17,5 / 20** |
| **Génie logiciel & Architecture** | Stack robuste, séparation des couches, sécurité RBAC, principe des 4 yeux | **17,0 / 20** |
| **Cœur scientifique & IA** | Protocole rigoureux (découpage temporel sans fuite), métriques justifiées (PR-AUC) | **18,5 / 20** |
| **Adéquation métier (Sopra HR)** | Compréhension fine de la paie et de la conformité bancaire, zéro survente | **18,0 / 20** |
| **Qualité rédactionnelle** | Style académique soigné, rapport condensé en 5 chapitres (104 p.), zéro warning | **17,0 / 20** |

---

## 2. LA QUESTION PIÈGE DU JURY SUR L'IA

### La question posée par le jury :
> *« Pourquoi n'avez-vous pas changé de modèle alors que votre score n'est que de ~40 % ? C'est insuffisant, il existe d'autres algorithmes plus performants ! »*

---

### Pourquoi c'est un piège et comment le désamorcer :

#### A. Ce n'est PAS de l'« Accuracy » (Exactitude brute) :
- Les anomalies ne représentent que **2 %** des fiches (98 % sont des fiches normales).
- Un modèle naïf ou « idiot » qui prédirait toujours que tout est normal aurait **98 % d'accuracy**... mais il serait **totalement inutile** car il laisserait passer 100 % des erreurs et fraudes !
- Vos ~40 % représentent la **PR-AUC (41 %)** et le **F1-score (42 %)**.
- Sur une classe positive rare (2 %), une PR-AUC de 41 % est **20 fois plus discriminante qu'un tirage aléatoire**.

#### B. Vous AVEZ testé et benchmarké d'autres modèles (Chapitre 4) :
Vous n'êtes pas restée sur un seul modèle :
- **Règles métier seules :** $F1 = 0,28$ (PR-AUC = 0,11) — insuffisant pour les atypismes subtils.
- **Local Outlier Factor (LOF) :** $F1 = 0,08$ — proche du hasard complet, rejeté.
- **One-Class SVM :** $F1 = 0,51$ — performances comparables à Isolation Forest.
- **Isolation Forest avec sélection de variables :** $F1 = 0,62$.
- **Modèle Hybride Optimisé (Règles + Isolation Forest sans montant brut) :**  
  **$F1 = 0,75$**, **Précision = 88 %**, **Rappel = 66 %**, et seulement **2 faux positifs pour 1 000 fiches** !

#### C. La maturité industrielle de ne pas déployer la v2 sans pilote réel :
- Le modèle candidat optimisé a été évalué sur des données synthétiques.
- Dans le secteur bancaire et de la paie, déployer une v2 en aveugle sans pilote sur des salaires réels viole les règles de sécurité industrielle. Le modèle candidat est donc packagé et prêt, en attente de validation terrain.

---

### Réponse type en 3 points à prononcer le jour J :

> **1. Précision sur la métrique :**  
> *« Monsieur/Madame le membre du jury, ce chiffre de 40 % n'est pas une exactitude (accuracy), mais une PR-AUC et un score F1 sur un jeu de données très déséquilibré où les anomalies ne représentent que 2 %. Un modèle naïf qui ne signalerait rien aurait 98 % d'accuracy tout en étant totalement inopérant. Une PR-AUC de 0,41 démontre que notre modèle est 20 fois supérieur au hasard. »*

> **2. La comparaison d'autres modèles a bien été menée :**  
> *« Ensuite, j'ai justement comparé plusieurs familles d'algorithmes au Chapitre 4 : le Local Outlier Factor (LOF), le One-Class SVM et les règles métier seules. Le LOF est inopérant (F1 de 0,08) et le One-Class SVM est équivalent à l'Isolation Forest (F1 de 0,51).  
> Ce qui a fait bondir les performances n'est pas l'algorithme brut, mais l'ingénierie des variables et l'hybridation : notre modèle Hybride Optimisé (Règles + Isolation Forest sans le montant brut) atteint un score F1 de **0,75**, avec **88 % de précision** et seulement **2 faux positifs pour 1 000 fiches**. »*

> **3. La rigueur industrielle de Sopra HR :**  
> *« Enfin, ne pas activer immédiatement ce modèle candidat en production est un choix délibéré de prudence industrielle. Sur de la paie d'entreprise, on ne déploie pas une nouvelle version optimisée sur données synthétiques sans une phase préalable de pilote et de retour d'expérience avec les gestionnaires RH. Le modèle v2 est donc packagé comme modèle candidat, la v1 assurant la stabilité opérationnelle. »*

---

## 3. LES 8 QUESTIONS CLÉS DU JURY ET RÉPONSES TYPES

### Q1. *« Pourquoi Isolation Forest plutôt que le Deep Learning / Réseaux de neurones ? »*
> **Réponse :**  
> En détection d'anomalies de paie, nous manquons d'historique étiqueté de fraudes réelles (apprentissage non supervisé obligatoire) et nous avons un impératif absolu d'explicabilité pour les gestionnaires RH. Isolation Forest est léger, rapide, robuste sur données tabulaires de dimension modérée et n'exige pas des millions de paramètres comme le Deep Learning. Notre étude comparative a montré que la valeur vient des variables comportementales (écarts relatifs au salarié et à la société) et non de la complexité mathématique de l'algorithme.

### Q2. *« Quelle est la différence entre validation XSD, contrôles métier et contrôle IA ? »*
> **Réponse :**  
> Ce sont trois niveaux complémentaires et étanches :
> - **Validation XSD :** vérifie la structure syntaxique du XML contre le schéma bancaire (balises obligatoires, formats, longueurs).
> - **Contrôles métier :** vérifient les règles déterministes bloquantes (IBAN modulo 97, BIC valide, doublons stricts, montants > 0).
> - **Contrôle IA :** surveillance statistique non bloquante qui alerte sur des montants atypiques pour un salarié donné, avec explication et recommandation.

### Q3. *« Comment garantissez-vous que le Manager ne valide pas son propre fichier ? »*
> **Réponse :**  
> La règle des quatre yeux est verrouillée côté serveur dans Spring Boot (`FichierWorkflowService`), et pas seulement masquée dans l'interface React. Le backend vérifie l'identité du demandeur : si l'utilisateur qui soumet tente aussi de valider, l'API renvoie un code `HTTP 403 Forbidden` et trace la tentative dans le journal d'audit.

### Q4. *« Pourquoi l'administrateur technique ne voit-il pas les salaires des employés ? »*
> **Réponse :**  
> Par application stricte du principe de moindre privilège et de conformité RGPD / protection des données personnelles. L'administrateur gère l'infrastructure, les comptes et les référentiels de sociétés. Les rémunérations et IBAN nominatifs relèvent de la confidentialité RH : l'administrateur dispose uniquement d'une vue de supervision globale agrégée, sans accès aux matricules ni aux montants individuels.

### Q5. *« Que se passe-t-il si le service Python d'IA est en panne ou arrêté ? »*
> **Réponse :**  
> L'architecture applique un principe de résilience non bloquante. Le client HTTP de Spring Boot utilise un timeout strict de 5 secondes. En cas d'indisponibilité du conteneur IA, l'incident est intercepté sans faire planter le contrôle : le fichier XML est généré et validé par le XSD et les règles métier, et la synthèse indique explicitement que le contrôle IA est indisponible (validé lors du test de recette R1).

### Q6. *« Pourquoi avoir choisi React plutôt qu'Angular (souvent cité dans les cahiers des charges) ? »*
> **Réponse :**  
> React 18, associé à l'outil de construction rapide Vite et à la bibliothèque Ant Design, offre un cycle de développement très court et des composants riches particulièrement adaptés aux formulaires complexes, tableaux paginés et parcours par étapes de notre plateforme. Ce choix a facilité le prototypage itératif imposé par la méthode Scrum.

### Q7. *« Quelles anomalies l'IA ne détecte-t-elle pas et pourquoi ? »*
> **Réponse :**  
> Les salariés fantômes et les salariés sortis encore payés restent à 0 % de rappel pour le modèle d'IA seul. Ce n'est pas un défaut d'algorithme : ces anomalies présentent des montants plausibles. Pour les détecter, il faut croiser les flux avec des sources externes (registre du personnel, badgeuse, SIRH) que le fichier de paiement seul ne contient pas.

### Q8. *« Vos 1 100 fiches contrôlées en ~3 à 9 secondes constituent-elles un test de charge ? »*
> **Réponse :**  
> Non, c'est une mesure de performance fonctionnelle en environnement de démonstration, et non un test de stress industriel. Pour passer à l'échelle sur 50 000 ou 100 000 salariés mensuels, il faudrait introduire un traitement asynchrone par file de messages (type RabbitMQ/Kafka) et du partitionnement de base de données.

### Q9. *« Quelle est la durée de vos sprints ? Respectent-ils le guide Scrum officiel ? »*
> **Réponse :**  
> Oui, absolument. Le guide Scrum (*Schwaber & Sutherland*) stipule qu'un sprint a une durée fixe maximale d'un mois civil (quatre semaines). Notre projet de 7 mois (28 semaines) est rigoureusement articulé en 5 sprints de réalisation de quatre semaines chacun (avril à août), précédés d'une étude préalable de quatre semaines (mars) et suivis d'une phase de finalisation de quatre semaines (septembre). Aucun sprint ne dépasse cette limite réglementaire.

### Q10. *« Pourquoi avoir ajouté les méthodes dans le diagramme de classes ? Quel est leur apport ? »*
> **Réponse :**  
> Un diagramme de classes qui n'affiche que des attributs ressemble à un modèle conceptuel de données (MCD) relationnel. En orienté objet, la classe regroupe à la fois l'état (les attributs) et le comportement (les méthodes). L'ajout des méthodes métier (ex. `verifierIban()`, `approuver()`, `validerXsd()`, `soumettre()`, `ignorer()`) explicite les responsabilités de chaque entité dans les processus de gestion et de validation des flux SEPA.

### Q11. *« Pourquoi avoir atomisé les bulles et séparé le diagramme de cas d'utilisation en périmètres de rôles ? »*
> **Réponse :**  
> C'est une règle fondamentale de modélisation UML : un cas d'utilisation doit être atomique et ne jamais combiner deux actions distinctes avec un « et » ou un « ou ». Nous avons donc scindé les cas composés (*Valider un fichier* vs *Rejeter un fichier*, *Générer* vs *Contrôler*, *Gérer les utilisateurs* vs *Valider les inscriptions*, etc.). De plus, regrouper les cas par périmètre de responsabilité (Administrateur, Manager, Expert RH, Fonctions communes) rend le diagramme parfaitement lisible et matérialise le cloisonnement strict des privilèges de la plateforme.

---

## 4. AXES D'AMÉLIORATION POUR VISER LA NOTE DE 19/20

Pour transformer un 17,5/20 en **19/20**, voici les éléments à valoriser :

### A. Sur le rapport :
1. **Ajout d'un Executive Summary (Fiche d'impact 1 page) :**  
   Placé juste après l'Abstract pour donner aux jurys pressés la synthèse des résultats clés : 100 % conformité XSD, F1 hybride 0,75, 205 tests automatisés, recette E2E 40/40.
2. **Tableau de Retour sur Investissement (ROI) / Gains Métier :**  
   Chiffrer le passage de 4-8 heures de contrôles manuels fastidieux à moins de 5 minutes automatisées, avec suppression du risque de pénalités bancaires dues à un rejet de lot.
3. **Matrice de conformité Sécurité & RGPD :**  
   Mettre en valeur la désactivation des entités externes XML (protection contre les failles XXE), le hachage BCrypt, l'OTP éphémère et le masquage des coordonnées bancaires.
4. **Conformité UML intégrale des diagrammes (Terminé) :**  
   Tous les diagrammes de cas d'utilisation (global et sprints 1 à 5) sont strictement atomiques (zéro bulle combinée avec « et » / « ou ») et structurés en périmètres clairs par rôle. Le diagramme de classes comporte l'intégralité de ses méthodes métier avec types et visibilités.

### B. Sur l'application :
1. **Pipeline CI/CD (GitHub Actions) :**  
   Ajouter un workflow `.github/workflows/ci.yml` pour prouver l'automatisation des tests et builds.
2. **Bouton de scénario de démonstration rapide :**  
   S'assurer que la base peut être réinitialisée et le lot de 1 100 fiches rejoué en un clic lors de la soutenance.

---

## 5. CONSEILS STRATÉGIQUES POUR LE JOUR J (ORAL ET DÉMO)

1. **Gestion du temps de parole (20-25 min d'exposé) :**
   - 3 min : Contexte (Sopra HR, la paie, l'enjeu financier du rejet bancaire SEPA).
   - 4 min : Problématique et Architecture en services (séparation des rôles, principe des 4 yeux).
   - 6 min : Démonstration dynamique ciblée.
   - 8 min : Cœur scientifique de l'IA (comparaison des modèles, ablation, hybridation, limites admises).
   - 3 min : Bilan, recette de bout en bout et perspectives.
2. **Ce qu'il faut montrer pendant la démo :**
   - Ne perdez pas de temps à remplir des formulaires champ par champ.
   - Chargez directement le fichier de masse (1 100 fiches).
   - Montrez le résultat du contrôle : la carte synthétique, le tableau IA avec l'anomalie mise en évidence (ex : décalage de virgule ou IBAN modifié), l'explication en français et la recommandation d'action.
   - Tentez de valider avec le compte Expert RH pour prouver devant le jury que le serveur bloque physiquement l'action (HTTP 403 - Règle des quatre yeux).
3. **La phrase clé à retenir pour conclure votre soutenance :**
   > *« Mon objectif en tant qu'ingénieure n'était pas de remplacer le gestionnaire RH par une intelligence artificielle opaque, mais de lui fournir un outil d'aide à la décision explicable, robuste et sécurisé, garantissant à la fois la conformité bancaire internationale et l'intégrité des paiements de l'entreprise. »*
