# Scénario de soutenance

Un fichier de paie de **1 100 fiches** pour la société de démonstration, avec **12 anomalies connues** (9 types) et
9 fiches normales qui ressemblent à des anomalies (primes, changements d'IBAN légitimes). Données générées par
`python -m benchmark.describe_dataset` (graine 2026, déterministe).

| Fichier | Contenu |
|---|---|
| `historique_6_mois.csv` | 6 mois de virements corrects |
| `fichier_paie_mois.csv` | Le fichier de paie à contrôler : 1 100 fiches |
| `anomalies_attendues.csv` | Les 12 fiches anormales connues, avec type et description |
| `cas_normaux_atypiques.csv` | Fiches normales qui ressemblent à des anomalies (un signalement est discutable, pas une erreur du jeu) |

## Mode d'emploi

### 1. Lancer l'application
```
docker compose up -d
```
(frontend http://localhost:5173, MailHog http://localhost:8025 pour le code OTP).

### 2. Importer le scénario
Depuis `ai-module/` :
```
.\.venv\Scripts\python.exe -m benchmark.importer_scenario
```
L'import **n'efface aucune donnée existante** : il ajoute la société `SOC-DEMO`, son affectation à `expert1`, 6 mois
d'historique (les 6 mois précédant le mois courant) et les 1 100 fiches du mois courant (statut APPROUVEE).
- `--reinitialiser` supprime d'abord les données `SOC-DEMO` puis réimporte ;
- `--retirer` supprime uniquement les données `SOC-DEMO` (à faire après la soutenance : l'import ajoute 7 742 lignes, ce qui
  alourdit la page Transactions).

### 3. Dérouler la démonstration
1. **expert1** (mot de passe `expert1123`) → *Virements de masse* : société `SOC-DEMO`, période du mois courant, tous les
   paiements, schéma `pain.001.001.03.xsd`, puis *Lancer le contrôle* (≈ 7 s).
2. **Synthèse** : carte *Contrôle IA* « 9 fiches suspectes sur 1100 (0,8 %) » avec la répartition par type ; carte
   *Contrôles métier* « 1 erreur » (montant nul) et 1 avertissement (doublon).
3. Onglet **Contrôle IA** : 9 fiches triées par score, avec niveau, type probable, explication et recommandation.
4. *Soumettre à validation*, puis **manager1** (`manager1123`) → *Fichiers à valider* : mêmes informations, puis validation.
5. **manager1** → *Indicateurs KPI* (mois courant) : cartes « Anomalies détectées par l'IA » et « Part détectée par l'IA seule ».
6. **admin** (`admin123`) → *Contrôle / Qualité du modèle IA* : version, seuil, performances, courbe précision–rappel.
   L'administrateur n'a accès à aucune donnée de salarié.

### 4. Ce qu'on doit voir (modèle en service, mesuré le 15/11/2026)

| Anomalie connue | Résultat à l'écran |
|---|---|
| Montant × 3,1 (EMP00407) | Contrôle IA, niveau élevé, « Montant très supérieur à l'habituel » |
| Erreur de virgule ×100 (EMP01027) | Contrôle IA, niveau élevé, « Erreur de virgule probable » |
| Hausse de 43 % (EMP00325) | Contrôle IA, niveau moyen, « Hausse inhabituelle » |
| IBAN modifié + montant élevé (EMP00529) | Contrôle IA, niveau élevé |
| IBAN partagé, 1re fiche (EMP00525) | Contrôle IA, niveau élevé, « IBAN partagé » |
| Montant nul (EMP00252) | *Contrôles métier* : erreur |
| Doublon (EMP00284 ×2) | *Contrôles métier* : avertissement |
| IBAN partagé 2e fiche, erreur de virgule ÷100, salarié fantôme, salarié sorti encore payé | **Non détectés** (limites mesurées dans le rapport) |

Au total : **5 anomalies sur 12 par l'IA**, 2 de plus par les contrôles métier, et **4 fausses alertes IA** (3 changements d'IBAN
légitimes et 1 fiche au profil inhabituel). C'est le comportement du modèle **en service** ; la version candidate
(`candidat_v2/`, non activée) détecterait davantage d'anomalies avec moins de fausses alertes sur le banc d'essai.
