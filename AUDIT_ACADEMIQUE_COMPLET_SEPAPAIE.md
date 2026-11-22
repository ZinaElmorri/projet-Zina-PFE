# Logistique

- **Fichier LaTeX principal** : `rapport_pfe/main.tex` (contient `\documentclass[12pt,a4paper,oneside]{report}` et `\begin{document}`). Il inclut par `\input` : `preamble`, `front/titlepage`, `front/dedication`, `front/thanks`, `front/acronyms`, `chapters/intro_generale`, `chapters/chapter1` à `chapter8`, `chapters/conclusion_generale`, et la bibliographie `bibliography.bib`.
- **PDF audité** : `rapport_pfe/main.pdf`.
- **Date de compilation** : 17/11/2026 (PDF postérieur à la dernière modification des `.tex` : à jour, recompilé après ajout de l'introduction et de la conclusion générales).
- **Nombre de pages** : 114 pages PDF ; pagination imprimée romaine i–xv (pages liminaires) puis arabe 1–98.
- **Méthode de pagination / décalage** : décalage **constant** par bloc.
  - Pages romaines : `page imprimée N = page PDF N+1` (la page de garde est la page PDF 1, non numérotée ; Dédicace = i = PDF 2).
  - Pages arabes : `page imprimée N = page PDF N+16` (Introduction générale = imprimée 1 = PDF 17 ; Références = imprimée 98 = PDF 114).
- **Statut de compilation** : succès. Aucun `Overfull`, `Missing character` ou référence `undefined` dans `main.log`. Restent uniquement : un avertissement `fancyhdr \headheight too small` (bénin) et quelques `Underfull \hbox (badness 10000)` sur des URL longues de la bibliographie (cosmétique).
- **Sources IA de référence lues** : `ai-module/benchmark/rapport/resultats.json`. Tous les chiffres chiffrés du chapitre 7 ont été confrontés à ce fichier.

---

# A. Bloquants

## A1. Résumé (FR), Abstract (EN) et mots-clés absents

- **Page imprimée** : — (emplacement attendu : pages liminaires, après les remerciements ; mots-clés en fin de résumé/abstract).
- **Page PDF** : — (section inexistante).
- **Fichier LaTeX** : aucun. Le dossier `front/` ne contient que `titlepage.tex`, `dedication.tex`, `thanks.tex`, `acronyms.tex`. `main.tex` ne contient aucun `\input` de résumé ni d'abstract.
- **Section / tableau / figure** : pages liminaires.
- **Constat** : le rapport ne comporte **ni résumé en français, ni abstract en anglais, ni liste de mots-clés**. C'est une pièce exigée pour un dépôt de PFE (structure SESAME).
- **Risque** : refus / renvoi au dépôt ; un jury lit souvent le résumé en premier.
- **Correction exacte recommandée** : créer `front/resume.tex` (résumé FR ≈ 150–200 mots + 6–8 mots-clés) et `front/abstract.tex` (traduction fidèle + keywords), puis les inclure dans `main.tex` après `\input{front/thanks}`. Mots-clés cohérents avec le contenu réel : SEPA, ISO 20022, pain.001, validation XSD, détection d'anomalies, Isolation Forest, Spring Boot, React. L'abstract doit reprendre **les mêmes résultats** que le corps (précision 0,47 ; rappel 0,38 ; F1 0,42 ; PR-AUC 0,41 ; hybride F1 0,75) et **ne pas** présenter la reconnaissance faciale, l'OTP, etc. comme autre chose que ce que fait réellement l'application.
- **Priorité** : **critique**.

## A2. Cohérence des rôles — CONFORME

- **Constat** : recherche exhaustive des anciens intitulés (`Gestionnaire RH`, `GESTIONNAIRE_RH`, `Validateur/VALIDATEUR`, `Auditeur/AUDITEUR`, `Chef d'équipe`) sur tous les `.tex` : **aucune occurrence**. Seuls les trois rôles finaux sont utilisés (`Administrateur`, `Manager`, `Expert RH`), de façon homogène. Les occurrences de « validateur » dans `chapter5.tex:149` et `chapter5.tex:185` désignent le **validateur XSD** (outil), pas un rôle.
- **Risque** : nul.
- **Priorité** : aucun correctif. Élément **finalisé**.

## A3. Architecture et technologies — CONFORME (1 point de vigilance mineur)

- **Constat** : React 18 + Vite (frontend), Spring Boot 3 / Java 17, PostgreSQL 16, Python/FastAPI + scikit-learn, Docker Compose — présentés de façon cohérente entre `chapter3.tex` (tableau `tab:technologies`), les chapitres de réalisation et la conclusion. Aucune trace d'Angular. Les versions `pain.001.001.03` et `.09` sont mentionnées (`chapter1.tex:39`), avec la version 03 par défaut (`chapter5.tex:185`). La reconnaissance faciale et l'OTP sont **réellement implémentés** (tests sprint 1, vérifiés par la recette A1–A5) : leur présentation comme « réalisés » est donc correcte.
- **Point de vigilance (mineur)** : la figure de déploiement (`fig3_2_deploiement_local`) a été corrigée (« Serveur web statique » au lieu de « Serveur Vite »), mais le tableau `tab:technologies` (`chapter3.tex:205`) liste encore « React 18, **Vite** et Ant Design ». C'est **correct** (Vite = outil de construction), mais veiller à ne pas laisser croire que l'interface est servie en production par le serveur de développement Vite. Aucune action obligatoire.
- **Priorité** : mineure.

## A4. Populations d'évaluation — CONFORME

- **Constat** : le chapitre 7 distingue clairement (a) le **jeu synthétique de référence** (9 sociétés, 4 621 salariés, 18 mois, 73 568 fiches, 1 517 anomalies à 2,06 %, découpage 1026/242/249), (b) le **scénario de démonstration** (1 100 fiches, 12 anomalies — `chapter7.tex:513`, `chapter8.tex:219`), (c) les **données de démonstration de l'application**. Chaque chiffre du tableau `tab:jeu-donnees` a été vérifié identique à `resultats.json` (y compris 749 primes, 1 986 treizièmes mois, 152 IBAN légitimes).
- **Risque** : nul. Élément **finalisé**.

## A5. Résultats et anciennes valeurs — CONFORME

- **Constat** : recherche des anciennes valeurs (`2 250 %`, `45 / 2`, `8 sociétés × 30 salariés`, `5 fichiers`) : **aucune occurrence**. Les valeurs de démonstration (`0,8 %` du scénario `chapter7.tex:551` ; `0,18 %` / `0,27 %` des captures de tableau de bord) sont explicitement encadrées comme démonstration et « sans valeur de performance réelle » (`chapter8.tex:144`). Les résultats finaux (0,47 / 0,38 / 0,42 / PR-AUC 0,41 / 8,9 FP ; hybride F1 0,75 / PR-AUC 0,71) sont **identiques** entre texte, tableaux `tab:perf-service`, `tab:comparaison` et `resultats.json`. Le seuil 0,6 inefficace (effectif 0,5) est traité honnêtement (`chapter7.tex:331`).
- **Taux d'erreur KPI** : défini dans `chapter8.tex` (`tab:kpi`) : « Fiches approuvées portant au moins une erreur bloquante non ignorée, rapportées aux fiches approuvées ». Cohérent avec l'info-bulle du frontend (`KpiDashboard.jsx:341`). **Défini**.
- **Priorité** : aucun correctif sur les valeurs. Élément **finalisé**. (Voir B2 pour la distinction des deux montants.)

## A6. Tableaux — voir section E (aucun tableau incomplet bloquant)

## A7. Résumé / Abstract — voir A1 (bloquant).

## A8. Conclusion générale — CONFORME

- **Page imprimée** : 95–97 — **page PDF** : 111–113.
- **Constat** : résultats / limites / perspectives distingués ; pas d'ancienne métrique ; perspectives réalistes en trois axes (intégration, IA, sécurité/exploitation) ; emploi du terme « rapport ». Correction déjà appliquée : retrait de « net supérieur au brut » de la liste des contrôles bloquants (non alimenté par la détection, cf. `chapter6.tex:146`), et reformulation de la reconnaissance faciale comme « autre mode de connexion ».
- **Priorité** : aucun correctif. Élément **finalisé**.

---

# B. Contenu et cohérence

## B1. Référence scientifique Isolation Forest manquante

- **Page imprimée** : 23 (citation) — **page PDF** : 39 ; bibliographie page imprimée 98 / PDF 114.
- **Fichier LaTeX** : `chapter3.tex:78` + `bibliography.bib`.
- **Section** : 3.3.2 « Intérêt de l'apprentissage non supervisé » ; `@misc{sklearn-iforest}`.
- **Constat** : Isolation Forest, **algorithme au cœur du projet**, n'est cité que par la **documentation scikit-learn**. L'article fondateur n'est pas référencé.
- **Risque** : **élevé** côté jury Data Science (« Quelle est la source de l'algorithme ? »).
- **Correction exacte recommandée** : ajouter dans `bibliography.bib` la référence réelle et la citer en `chapter3.tex:78` :
  `@inproceedings{liu2008isolation, author={Liu, Fei Tony and Ting, Kai Ming and Zhou, Zhi-Hua}, title={Isolation Forest}, booktitle={2008 Eighth IEEE International Conference on Data Mining (ICDM)}, pages={413--422}, year={2008}}`.
- **Priorité** : importante.

## B2. Deux montants du tableau de bord non distingués dans le texte

- **Page imprimée** : 87–88 — **page PDF** : 103–104.
- **Fichier LaTeX** : `chapter8.tex` (`tab:kpi` ligne 123 ; figure `fig:dash-admin`).
- **Section** : 8.4.2 / 8.5.1.
- **Constat** : la figure du tableau de bord administrateur (`fig8_dashboard_admin`) affiche **deux** montants : « Montant des fiches approuvées » (bloc mensuel) et « Montant des fichiers » (colonne du tableau « Par société »). Le tableau `tab:kpi` ne définit que le premier ; le second n'est pas expliqué dans le texte.
- **Risque** : moyen — question du jury « Pourquoi deux montants, et quelle différence ? ».
- **Correction exacte recommandée** : ajouter une phrase en 8.4.2, par exemple : « Le tableau par société cumule le montant des fichiers SEPA générés (un salarié est compté une fois par fichier), distinct du montant des fiches approuvées du mois : le premier porte sur les fichiers produits, le second sur les fiches validées par le Manager. »
- **Priorité** : importante.

## B3. Chronologie (Gantt) présentée comme réalisée

- **Page imprimée** : 17–18 — **page PDF** : 33–34.
- **Fichier LaTeX** : `chapter2.tex:220` (« Le projet s'est déroulé sur sept mois, soit environ trente semaines ») + figure `fig:gantt` + tableau `tab:sprints`.
- **Section** : 2.6.3 « Planification des sprints ».
- **Constat** : le calendrier (Mars→Septembre, durées 4–6 semaines par sprint) est présenté au passé comme une chronologie **réelle**. Le découpage interne est cohérent (0→30 semaines, « sept mois »), mais il s'agit d'une planification. Le stage ayant débuté le 1ᵉʳ mars 2026 pour 7 mois, l'intitulé « s'est déroulé » doit pouvoir être défendu comme le déroulement effectif.
- **Risque** : moyen — question « Ce Gantt est-il prévisionnel ou constaté ? ».
- **Correction exacte recommandée** : soit confirmer qu'il reflète le déroulement réel (et le laisser tel quel), soit nuancer : « La planification retenue, présentée ci-dessous, s'est étalée sur sept mois ». À arbitrer selon la réalité.
- **Priorité** : mineure.

## B4. Module IA — CONFORME (point fort)

- **Pages imprimées** : 65–82 — **pages PDF** : 81–98.
- **Constat** : le chapitre 7 répond à toutes les questions attendues : rôle de l'IA (aide à la décision non bloquante, `chapter7.tex:47`), features définies et justifiées (`tab:variables-ia`), choix d'Isolation Forest justifié par la comparaison (`tab:comparaison`), méthode de choix du seuil (maximisation du F1 sur la validation, conforme à `resultats.json/evaluation/protocole/choixSeuil`), protocole chronologique sans fuite, PR-AUC comme métrique principale justifiée par le déséquilibre, détection par type, types non détectés (salarié fantôme / sorti payé), valeur ajoutée face aux règles, explicabilité, limites (données synthétiques).
- **Priorité** : aucun correctif. Élément **finalisé** et défendable.

## B5. Backlog / US / sprints — CONFORME

- **Constat** : 24 US réparties sur 5 sprints ; acteurs = rôles finaux ; cohérence backlog produit (`chapter2.tex`) / backlog par sprint (chapitres 4–8). US21–US24 bien rattachées au sprint 5. Éléments « prévus mais non alimentés » signalés explicitement (`chapter6.tex:146`).
- **Priorité** : aucun correctif.

---

# C. Forme et mise en page

## C1. ROC-AUC et PR-AUC absents de la liste des abréviations

- **Page imprimée** : xv (liste des abréviations) ; usage en 72–79 — **page PDF** : 16 ; usage PDF 88–95.
- **Fichier LaTeX** : `front/acronyms.tex` ; usages en `chapter7.tex:293-294`, etc.
- **Constat** : `PR-AUC` et `ROC-AUC` sont utilisés de façon intensive au chapitre 7. `PR-AUC` est développé **une fois** dans le texte (« aire sous la courbe précision–rappel (PR-AUC) », `chapter7.tex:205`), mais **`ROC-AUC` n'est jamais développé** et aucun des deux ne figure dans la liste des abréviations (qui recense API, BIC, EPC, HTTP, IA, IBAN, ISO, JPA, JSON, JWT, KPI, OTP, REST, RH, SCT, SEPA, SIRH, SMTP, XML, XSD).
- **Risque** : moyen — abréviations centrales du chapitre « scientifique » non définies en liste.
- **Correction exacte recommandée** : ajouter dans `front/acronyms.tex` :
  `\item[PR-AUC] Aire sous la courbe précision--rappel (\textit{Precision--Recall Area Under Curve})`
  `\item[ROC-AUC] Aire sous la courbe ROC (\textit{Receiver Operating Characteristic Area Under Curve})`.
- **Priorité** : importante.

## C2. Annexes prévues au plan mais absentes

- **Page imprimée** : — (après la conclusion, avant/après les références).
- **Constat** : le plan (`plan_rapport_pfe.md`) prévoit des annexes (catalogue des recommandations, principaux points d'accès REST, scénario de démonstration). Le rapport n'en contient aucune.
- **Risque** : moyen — non bloquant si l'établissement ne l'exige pas, mais des annexes courtes renforcent la défendabilité (endpoints REST, catalogue des recommandations).
- **Correction exacte recommandée** : créer `chapters/annexes.tex` (3–4 annexes courtes) et l'inclure après la conclusion, avec `\appendix`. Facultatif mais recommandé.
- **Priorité** : importante.

## C3. Pagination / pages blanches — CONFORME

- **Constat** : aucune page quasi vide détectée dans le PDF (vérification programmatique). Décalage de pagination constant (voir Logistique). Table des matières, table des figures, liste des tableaux et liste des abréviations présentes et référencées dans la table des matières.
- **Priorité** : aucun correctif.

## C4. URL de bibliographie en `Underfull hbox`

- **Page imprimée** : 98 — **page PDF** : 114.
- **Constat** : plusieurs `Underfull \hbox (badness 10000)` sur les URL longues de l'EPC (coupures larges). Rendu acceptable, purement cosmétique.
- **Correction recommandée** : charger `\usepackage{xurl}` (ou `url` avec `\urlstyle{same}`) dans `preamble.tex` pour autoriser la coupure des URL. Facultatif.
- **Priorité** : mineure.

---

# D. Formules à conserver, simplifier ou supprimer

| Formule | Page impr. (PDF) | Utilité | Décision | Justification |
|---|---|---|---|---|
| Clé IBAN modulo 97 | ch. 6, 55 (71) | Indispensable (contrôle réglementaire réel) | **Conserver** dans le corps, formulation courte | Contrôle métier effectivement implémenté ; attendu par le jury métier |
| Taux d'erreur KPI (fiches en erreur bloquante / fiches approuvées) | ch. 8, 88 (104) | Indispensable (indicateur de pilotage) | **Conserver** (déjà sous forme textuelle définie) | Défini sans ambiguïté et cohérent avec le code |
| Score d'anomalie / seuil (score ≥ 0,5 ou IBAN modifié) | ch. 7, 74 (90) | Indispensable (règle de décision) | **Conserver** | Décision réellement en service ; honnêteté sur le seuil 0,6 inopérant |
| Précision / Rappel / F1 / PR-AUC | ch. 7, 74 (90) | Indispensables (évaluation) | **Conserver** sous forme textuelle (déjà le cas) | Métriques standard, définies dans le texte |
| Features (écart à la moyenne du salarié, z-score société, variation mensuelle, IBAN modifié, nb paiements même IBAN) | ch. 7, 68 (84) | Utile | **Conserver** en tableau `tab:variables-ia` | Définies et justifiées par l'ablation |
| NbOfTxs / CtrlSum (en-tête pain.001) | ch. 5/6 | Utile (cohérence du fichier) | **Conserver** mention textuelle | Élément réel du message, pas besoin de formule développée |

Observation : le rapport privilégie déjà les définitions **textuelles** aux formules mathématiques développées, ce qui est adapté au « bon équilibre » demandé. Aucune formule à déplacer en annexe ni à supprimer.

---

# E. Tableaux incomplets ou mal placés

| Tableau | Page impr. (PDF) | Problème | Correction recommandée | Priorité |
|---|---|---|---|---|
| Aucun tableau incomplet détecté | — | Les tableaux audités (`tab:backlog-*`, `tab:roles-scrum`, `tab:sprints`, `tab:jeu-donnees`, `tab:perf-service`, `tab:comparaison`, `tab:ablation`, `tab:technologies`, `tab:kpi`, `tab:recette`, `tab:bilan`) ont tous en-têtes, unités et dénominateurs explicites, et sont commentés dans le texte | — | — |
| `tab:recette` (recette) | ch. 8, 93 (109) | Ligne « Total 40 » correcte ; vérifier à la relecture que la somme des « Cas » (5+7+3+5+11+4+3+1+1) = 40 | Cohérent (= 40) ; aucun correctif | mineure |
| Légendes sous les tableaux | global | Convention « caption en dessous » respectée (dont le `longtable` du backlog via `\endlastfoot`) | Aucun | — |

Aucun grand espace blanc causé par un tableau repoussé n'a été détecté dans le PDF (pas de page quasi vide). Pas de recours à `longtable` supplémentaire nécessaire.

---

# F. Figures à corriger ultérieurement

| Figure | Page impr. (PDF) | Problème | Correction visuelle recommandée | Priorité |
|---|---|---|---|---|
| Page de garde — logos | i (1) | Le logo Sopra HR est affiché ; vérifier qu'un **logo de l'université/SESAME** figure dans le bandeau `banner.png` et mentionner la source des logos | S'assurer que `banner.png` contient l'en-tête établissement ; citer la source des logos | importante |
| `fig1_1` Logo Sopra HR | ch. 1 | Logo d'entreprise sans mention de source | Ajouter « Source : site institutionnel Sopra HR » en légende | mineure |
| `fig8_dashboard_admin` | 87 (103) | Capture correcte et à jour (périmètre filtré), mais contient deux montants non explicités dans le texte | Aucune retouche image ; corriger le texte (cf. B2) | mineure |
| Captures d'interface (ch. 4–8) | 38–94 | Vérifier visuellement qu'aucune capture n'affiche un ancien rôle/menu ; les captures du sprint 5 ont été refaites après la correction du périmètre Expert RH | Contrôle visuel final ; cohérent à ce stade | mineure |
| Figures du banc d'essai (`fig7_*`) | 72–79 | Axes et légendes à vérifier en français et lisibles | Contrôle visuel de la taille de police des axes | mineure |

Aucune figure manquante : 54 `\includegraphics` audités, toutes les images résolvent via `\graphicspath{{images/figs/}}` (seul `\hostCompanyLogo`, une macro, se résout en `logo_sopra.png`). Aucune option `draft`, aucun chemin d'image affiché en clair.

---

# G. Anciennes valeurs ou formulations restantes

| Valeur / formulation | Page | Contexte | Action |
|---|---|---|---|
| « 21 fois supérieure » (PR-AUC vs hasard) | ch. 7, 74 (PDF 90), `chapter7.tex:300` | 0,407 / 0,0206 = **19,8** ≈ 20 ; « 21 fois » surévalue légèrement | Remplacer par « environ 20 fois » |
| Anciens rôles | — | Recherche exhaustive | **Aucune occurrence** (RAS) |
| `2 250 %`, `45 / 2`, `8 sociétés × 30 salariés`, `5 fichiers` | — | Anciennes valeurs du banc d'essai | **Aucune occurrence** (RAS) |
| `0,18 %` / `0,8 %` / `2 / 1100` présentés comme résultat | ch. 7–8 | Correctement encadrés comme « démonstration / sans valeur de performance réelle » | **RAS** (conforme) |

---

# H. Écarts entre le rapport et l'application réelle

| Élément | Ce que dit le rapport (page) | Ce que fait l'application (code) | Correction recommandée |
|---|---|---|---|
| Périmètre Expert RH (fiches + anomalies) | « l'Expert RH ne voit que les anomalies des fiches de ses sociétés » (ch. 6, `chapter6.tex:157`) ; « analyse à la demande sur les fiches visibles » (ch. 7) | `AccesSocietes.filtrerTransactions`, `TransactionController.list`, `AnomalieService.visibles/changerStatut`, `AiDetectionController` — filtrage serveur (commit `08cace9`), confirmé par recette S0/S1a/S1b | **Déjà aligné** (rapport corrigé + code corrigé) |
| Soumission d'un fichier en erreur métier | « la soumission est refusée (422) » (ch. 6/8) | `FichierWorkflowService.soumettre` refuse si `nbErreursMetier>0` (commit `72a49ad`), recette M3 | **Déjà aligné** |
| Séparation des rôles (Admin sans données de paie) | Admin = supervision sans donnée individuelle (ch. 8) | Recette D1–D3 (403 sur fiches, pas de matricule en supervision) | **Aligné** |
| Principe des quatre yeux | Expert soumet, Manager valide ; pas d'auto-validation (ch. 6) | Recette M7 (Expert 403), M11 (pas de revalidation) | **Aligné** |
| Mode dégradé IA | « le contrôle aboutit, IA déclarée indisponible » (ch. 7/8) | Recette R1–R3 (IA arrêtée → 200, `ia.disponible=false`) | **Aligné** |
| Seuil IA 0,6 vs 0,5 | « le seuil 0,6 n'a aucun effet, la décision est à 0,5 » (ch. 7, 74) | `docker-compose.yml` `AI_SCORE_THRESHOLD: "0.6"` ; décision native ≥ 0,5 | **Aligné** (traité honnêtement) |
| Deuxième montant du tableau de bord | Non expliqué | Colonne « Montant des fichiers » (Dashboard admin) | Ajouter la définition (cf. B2) |
| README du dépôt | Le rapport affirme que l'IA n'interroge jamais PostgreSQL (ch. 7) | `README.md` obsolète (4 anciens rôles, IA→DB) — **non repris** dans le rapport | Aucun impact sur le rapport ; mettre à jour le README hors rapport (facultatif) |

Aucun écart ouvert bloquant : les deux écarts historiques ont été corrigés **dans le code et dans le rapport**. Seul reste le point documentaire B2.

---

# I. Questions probables du jury

| Question | Page d'origine (PDF) | Risque | Réponse courte | Correction préventive |
|---|---|---|---|---|
| Où est l'IA dans le système et qu'apporte-t-elle de plus que les règles ? | ch. 7, 70–72 (86–88) | faible | Service Python appelé par le backend lors du contrôle ; elle détecte des montants atypiques invisibles aux règles (part « détectée par l'IA seule » ≈ 90 % sur le scénario). | RAS (déjà traité) |
| Pourquoi Isolation Forest plutôt que LOF / One-Class SVM / règles seules ? | ch. 7, 76–78 (92–94) | faible | Comparaison à protocole identique : LOF proche du hasard, One-Class SVM équivalent (F1 0,51), IF retenu pour son implémentation et sa stabilité ; le gain vient surtout des variables et de l'hybridation. | Citer Liu et al. 2008 (B1) |
| Comment le seuil a-t-il été choisi ? | ch. 7, 74 (90) | moyen | Maximisation du F1 sur la **validation** uniquement ; le seuil applicatif 0,6 est inopérant (décision native ≥ 0,5), ce qui est documenté. | RAS |
| Représentativité des données synthétiques / généralisation ? | ch. 7, 72 et 81 (88, 97) | **élevé** | Données réalistes mais synthétiques (paie confidentielle) ; limites explicitées ; validation sur données réelles prévue avant mise en service du candidat. | RAS (bien traité) |
| Pourquoi la PR-AUC ? Que signifient les faux positifs pour l'Expert RH ? | ch. 7, 72–74 (88–90) | faible | Anomalies rares (2 %), la PR-AUC est adaptée au déséquilibre ; ≈ 9 FP / 1 000 fiches = charge de vérification. | Définir ROC/PR-AUC en liste d'abréviations (C1) |
| Quelles anomalies l'IA rate-t-elle et pourquoi ? | ch. 7, 76 (92) | faible | Salarié fantôme et salarié sorti encore payé (rappel 0 %) : exigent des données externes (présence, registre), pas un autre algorithme. | RAS |
| Différence validation XSD / contrôles métier / contrôle IA ? | ch. 5–7 | faible | XSD = structure ; règles = erreurs certaines (IBAN, montant, doublon) bloquantes ; IA = avertissement sur montants atypiques. | RAS |
| Pourquoi React alors que le cahier citait Angular ? | ch. 3, 29 (45) | moyen | Choix justifié par l'écosystème et la rapidité de prototypage ; à confirmer oralement. | Ajouter une phrase de justification si le cahier impose Angular |
| Pourquoi l'Admin ne voit-il pas les données de paie ? | ch. 8, 87 (103) | faible | Moindre privilège / confidentialité : l'admin supervise le système, pas les salaires. | RAS |
| Les quatre yeux, garantis côté serveur ? | ch. 6, 53–57 (69–73) | faible | Oui : l'Expert ne peut pas valider (403 serveur), pas d'auto-validation ni de revalidation (recette M7, M11). | RAS |
| Que se passe-t-il si le service IA est indisponible ? | ch. 7/8, 67, 92 (83, 108) | faible | Mode dégradé : contrôle abouti, résultat « IA indisponible » (recette R1 : 200, `disponible=false`). | RAS |
| Différence entre les deux montants du tableau de bord ? | ch. 8, 87 (103) | moyen | Montant des fiches approuvées (mois) vs montant cumulé des fichiers générés (par société). | Expliciter dans le texte (B2) |
| Que reste-t-il à faire (révocation JWT, chiffrement, HTTPS, liveness) et pourquoi ? | conclusion, 96 (112) | faible | Listé en perspectives ; hors périmètre d'un prototype de recette. | RAS |
| Le Gantt est-il prévisionnel ou réel ? | ch. 2, 17 (33) | moyen | Déroulement effectif du stage sur 7 mois ; cohérent avec les livrables. | Clarifier la formulation (B3) |
| Que valent 1 100 fiches contrôlées en ~3–9 s — est-ce un test de charge ? | ch. 8, 92 (108) | moyen | Non : mesure fonctionnelle sur l'app déployée, pas un test de charge ; limite assumée. | RAS (déjà écrit) |

---

# J. Verdict final

- **Nombre de bloquants (critiques)** : **1** — Résumé FR + Abstract EN + mots-clés manquants (A1).
- **Nombre de corrections importantes** : **5** — référence Isolation Forest (B1) ; distinction des deux montants (B2) ; PR-AUC/ROC-AUC en liste d'abréviations (C1) ; annexes absentes (C2) ; vérification du logo université/source des logos sur la page de garde (F).
- **Nombre de corrections mineures** : **5** — « 21 fois » → « 20 fois » (G) ; formulation du Gantt (B3) ; coupure des URL biblio (C4) ; source des figures logo (F) ; vigilance « Vite » (A3).
- **Éléments considérés comme finalisés** : cohérence des rôles (A2) ; populations d'évaluation (A4) ; valeurs et résultats IA strictement alignés sur `resultats.json` (A5) ; chapitre 7 / module IA (B4) ; backlog et sprints (B5) ; conclusion générale (A8) ; absence d'anciennes valeurs et de pages blanches ; alignement rapport ↔ application (H).
- **Niveau de cohérence** : **9 / 10** (contenu homogène, chiffres vérifiés à la source, rôles propres ; seul manque documentaire : résumé/abstract).
- **Niveau de défendabilité devant jury** : **8 / 10** (atteindrait 9 après ajout du résumé/abstract, de la citation Isolation Forest et des deux abréviations).
- **Verdict** : **prêt après corrections bloquantes** — l'ajout du résumé/abstract/mots-clés (A1) est la seule correction critique ; les corrections importantes (B1, B2, C1, C2, F) sont fortement recommandées avant le dépôt.

AUDIT TERMINÉ — AUCUN FICHIER MODIFIÉ
