# Audit du rapport SEPAPaie — version 2 (état actuel : 8 chapitres, mise en page EYA)

Remplace `AUDIT_ACADEMIQUE_COMPLET_SEPAPAIE.md`, devenu périmé (il décrit l'ancienne version : « Références », Résumé absent, pagination à 114 pages, etc. — tous ces points sont corrigés).

Méthode : lecture des sources `.tex`, de `bibliography.bib`, du journal `main.log` et recoupement avec le code de l'application. Aucun fichier du rapport n'a été modifié par cet audit.

## Verdict : environ 18 / 20

Structure complète et cohérente : page de garde, autorisation de dépôt, dédicace, remerciements, résumé, abstract, tables (matières, figures, tableaux), acronymes, introduction générale, 8 chapitres (un par sprint, 4 semaines chacun), conclusion générale, Bibliographie, annexes. Les remarques du jury sont toutes appliquées.

## Ce qui est conforme (vérifié)

- **Compilation** : aucun `Overfull`, `Missing character`, référence ou citation indéfinie dans `main.log`.
- **Introduction générale** : annonce bien « huit chapitres », décrits dans le bon ordre ; 24 récits, 5 sprints, étude préalable.
- **Planification** : 28 semaines, sprints de 4 semaines, cohérent entre le chapitre 2, les introductions de sprint et le tableau de bilan.
- **Chiffres** : 178 tests backend, 27 tests IA, 40/40 recette, 52 chemins d'API, 5 services, 24 récits, identiques dans le chapitre 8, le résumé et l'application.
- **Références croisées** : plus aucun renvoi à `ch:realisation` ni aux anciens `sec:sprint*`.
- **Citations** : les 10 entrées de la bibliographie sont toutes citées (Isolation Forest : Liu 2008 + scikit-learn).
- **Honnêteté des résultats** : modèle déployé (F1 0,42, PR-AUC 0,41) distingué de l'hybride (F1 0,75), limites exposées, pas d'accuracy trompeuse.
- **Écran d'administration IA** : le chapitre 7 précise que le réentraînement passe par des points d'accès sans écran dédié ; confirmé (aucune page frontend correspondante).

## Points à corriger

### Importants

1. **Chapitre 7 sans aucune citation.** Le rapport compare LOF et One-Class SVM, utilise PR-AUC et ROC-AUC, mais ne cite aucune source. C'est la première attaque probable d'un jury de data science. Ajouter à `bibliography.bib` et citer dans la section « Comparaison des approches » et « Protocole et métriques » :
   - Breunig, Kriegel, Ng, Sander, *LOF: Identifying Density-Based Local Outliers*, SIGMOD 2000, p. 93–104.
   - Schölkopf et al., *Estimating the Support of a High-Dimensional Distribution*, Neural Computation 13(7), 2001, p. 1443–1471.
   - Saito & Rehmsmeier, *The Precision-Recall Plot Is More Informative than the ROC Plot When Evaluating Binary Classifiers on Imbalanced Datasets*, PLOS ONE 10(3), 2015.
   - Chandola, Banerjee, Kumar, *Anomaly Detection: A Survey*, ACM Computing Surveys 41(3), 2009 (cadrage de la détection d'anomalies, chapitre 3).
   Vérifier chaque référence avant ajout.
2. **Section « Tests et validation » seulement au sprint 4.** Les chapitres 4, 5 et 6 n'ont plus de bilan de tests par sprint (le tableau à 132 tests n'existe que dans `realisation.tex`, non inclus). Un jury peut le relever. Ajouter 3 à 5 lignes de tests par sprint (types de tests et résultats), ou une phrase renvoyant explicitement au bilan global du chapitre 8.
3. **`chapters/realisation.tex` à supprimer.** Hors du `main.tex`, mais il contient d'anciens chiffres (132 tests) contradictoires avec les 178 actuels ; risque de réutilisation par erreur.

### Moyens

4. **Chapitre 7 très long** (évaluation : lignes 145–480 environ, 21 sous-sections) comparé aux autres sprints. Cohérent avec l'importance de l'IA, mais il déséquilibre le rapport. Option : déplacer le détail de l'ablation et du rappel par type en annexe.
5. **Captures d'écran disponibles mais non utilisées** dans `images/figs` : `fig4_utilisateurs`, `fig4_affectations`, `fig5_approbation_fiche`, `fig5_xsd`, `fig6_a_valider`, `fig6_historique`, `fig6_modale_rejet`, `fig6_rejete`, `fig8_dashboard_manager`, `fig8_2_demarrage_conteneurs`, `fig7_ablation`, `fig7_rappel_type`. Plusieurs fonctionnalités décrites (gestion des utilisateurs, affectations, approbation, rejet avec motif) n'ont pas de capture dans « Interfaces réalisées ». Ajouter au moins `fig4_utilisateurs`, `fig4_affectations`, `fig6_modale_rejet` et `fig8_dashboard_manager`.
6. **Résumé / Abstract** : ne donnent que le résultat de l'hybride (F1 0,75). Ajouter une demi-phrase sur le modèle déployé (F1 0,42) pour que le résumé soit aussi honnête que le corps.
7. **US20 « superviser et réentraîner le module d'IA »** (backlog, chapitre 2) : préciser « via l'API d'administration », puisqu'il n'y a pas d'écran.

### Mineurs

8. **En-têtes** : 10 avertissements `fancyhdr` (`\headheight` à 14,5 pt, 15,13 pt requis). Dans `preamble.tex` ligne 58, passer à `\setlength{\headheight}{16pt}` ; l'avertissement disparaît et la mise en page ne change pas.
9. **Chiffres d'entreprise** (« plus de cinquante mille collaborateurs », « trentaine de pays ») : à garder uniquement si la source citée les confirme à la date de consultation.
10. **Année universitaire 2025 / 2026** en page de garde alors que le stage se termine en septembre 2026 et la soutenance a lieu en novembre : vérifier avec l'administration.
11. **Bibliographie** : toutes les entrées sont de type `@misc` ; après ajout des références scientifiques, vérifier que les types (`@inproceedings`, `@article`) sont corrects.

## Ordre d'exécution conseillé

1 (références du chapitre 7) → 2 (tests par sprint) → 3 (supprimer `realisation.tex`) → 5 (captures) → 6 et 7 (formulations) → 8 (en-tête) → recompilation par la tâche « Compiler Rapport PFE ».
