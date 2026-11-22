# Démo SEPAPaie — 5 minutes, de l'authentification à l'audit

Script **vérifié sur l'application réelle** (menus, boutons et comportements relevés dans le code et rejoués automatiquement par `demo_auto.mjs`).

## Avant de commencer (hors chrono)

1. Conteneurs démarrés : `sepa-frontend` (5173), `sepa-backend` (8080), `sepa-ai` (8000), `sepa-postgres`, `sepa-mailhog` (8025).
2. Données de démonstration : dans `ai-module`, `.\.venv\Scripts\python.exe -m benchmark.importer_scenario --reinitialiser`
   (société **SOC-DEMO**, 1 100 fiches approuvées, 12 anomalies, affectée à **expert1**). Retrait : `--retirer`.
3. Onglets ouverts : `http://localhost:5173/login` et **MailHog** `http://localhost:8025` (pour lire le code OTP).
4. Comptes : `expert1 / expert1123`, `manager1 / manager1123`, `admin / admin123`.
5. Lecture automatique de la démo (fenêtre Edge visible + bandeau de narration, à enregistrer avec Win+G ou OBS) :
   tâche VS Code **Demo** (`node demo_auto.mjs`). Les captures de contrôle sont écrites dans `demo_captures\`.

## Déroulé chronométré

| Temps | Rôle | Écran / action | Phrase à dire |
|---|---|---|---|
| 0:00–0:30 | — | **Créer un compte** : formulaire + photo du visage (webcam). | « L'inscription capture le visage ; un administrateur doit approuver le compte avant la première connexion. » |
| 0:30–1:00 | Expert RH | Connexion `expert1` + mot de passe → écran « Vérification de l'email » → code à 6 chiffres lu dans MailHog → *Vérifier*. Montrer le bouton « Connexion par reconnaissance faciale ». | « Double facteur : mot de passe puis code à usage unique, ou visage. Aucun jeton n'est émis avant le second facteur. » |
| 1:00–1:15 | Expert RH | **Tableau de bord** (à traiter) ; le menu ne montre que *Transactions, Virements de masse, Historique SEPA, Anomalies, Schémas XSD*. | « Le menu et les données dépendent du rôle et des sociétés affectées. » |
| 1:15–1:50 | Expert RH | **Transactions** : société SOC-DEMO, ID `EMP-DEMO1`, IBAN `FR7630006000011234567890188` (faux) → *Ajouter* (brouillon). Puis `EMP-DEMO2` avec IBAN `FR7630006000011234567890189` → statut « Non approuvé ». | « La saisie est limitée au périmètre de l'expert. L'IBAN est contrôlé au modulo 97 : la fiche invalide ne pourra pas être approuvée. » |
| 1:50–2:15 | Expert RH | Boutons **Contrôles métier** puis **Analyser (IA)** : montants suspects avec score, raisons et « Recommandation ». | « L'IA (Isolation Forest) explique chaque suspect, mais elle ne bloque jamais : sans IA, les contrôles métier continuent. » |
| 2:15–2:30 | Expert RH | Menu **Anomalies** : dérouler le détail, *Marquer corrigée* / *Ignorer*. | « Chaque anomalie a un statut de traitement tracé. » |
| 2:30–3:15 | Expert RH | **Virements de masse** → société SOC-DEMO, période, schéma XSD `pain.001.001.03`, *Tout sélectionner* → **Lancer le contrôle et générer** → onglets *Synthèse, XML, Validation XSD, Contrôle IA (9), Contrôles métier (1)*. | « En quelques secondes : contrôles métier, IA, XML pain.001 et validation XSD. Une erreur métier bloque la soumission ; un XML non conforme bloque le téléchargement. » |
| 3:15–4:00 | Manager | Déconnexion, connexion `manager1`. **Transactions** : *Approuver* `EMP-DEMO1` (refusé : IBAN invalide) puis `EMP-DEMO2` (approuvée). **Fichiers à valider** → *Examiner* → *Rejeter* (motif obligatoire, *Annuler*) → **Valider**. | « Principe des quatre yeux : le Manager valide ce que l'Expert prépare ; il ne peut pas valider son propre fichier. Avant validation, le téléchargement est préfixé `NON_VALIDE`. » |
| 4:00–4:15 | Manager | **Affectation des sociétés** puis **Indicateurs KPI**. | « Le Manager affecte les sociétés aux experts et suit les indicateurs. » |
| 4:15–4:50 | Administrateur | Connexion `admin` → **Utilisateurs** (approbation des inscriptions, activation) → **Sociétés débitrices** → **Schémas XSD** → **Historique SEPA** (supervision en lecture seule) → **Journal d'audit**. | « L'administrateur gouverne comptes, sociétés et schémas ; il n'accède à aucune donnée de paie individuelle ; toute action sensible est tracée. » |
| 4:50–5:00 | — | Swagger `http://localhost:8080/swagger-ui.html`. | « API REST documentée ; 178 tests Maven, 27 tests pytest, recette 40/40. » |

## Si quelque chose se passe mal

- **Code OTP introuvable** : rafraîchir MailHog ; le dernier message est le bon (*Renvoyer le code* sinon).
- **La page Transactions réagit lentement** : elle affiche toutes les fiches (1 100+) sans pagination ; préparer la saisie, puis cliquer.
- **« IA indisponible »** : normal si `sepa-ai` est arrêté ; c'est un point à valoriser (le traitement continue avec les contrôles métier, environ 6 s).
- **Aucun fichier dans « Fichiers à valider »** : un fichier doit avoir été soumis par l'expert (le script automatique le prépare via l'API).
- **Pas de visage reconnu** : utiliser le mot de passe + code, c'est le parcours nominal de la démo.

## Points que le jury peut demander

- Pourquoi l'expert ne valide-t-il pas ? → séparation des tâches (quatre yeux), 403 côté serveur.
- Où sont les données de paie pour l'admin ? → nulle part : supervision d'agrégats et du journal d'audit uniquement.
- L'IA est-elle bloquante ? → non, par conception (PR-AUC et benchmark dans le rapport).
