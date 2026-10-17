# Virements de masse SEPA (pain.001)

Génération de fichiers de virement SEPA ISO 20022 **conformes**, à partir d'un
XSD importé, avec validation XSD automatique, aperçu coloré et historique.
Fonctionnalité **isolée** : aucun impact sur l'existant (seuls `App.jsx` et
`Layout.jsx` ont été modifiés côté front, pour la route et le lien de menu).

## Parcours utilisateur

1. **Onglet XSD existant** : importer un XSD pain.001 (ex. `pain.001.001.03.xsd` officiel).
2. **Page « Virements de masse »** :
   - choisir le XSD (les XSD non compatibles pain.001 sont grisés) et consulter son **analyse**
     (version, namespace, ordre des éléments, obligatoires, `maxLength`, `pattern`, énumérations) ;
   - saisir la **société émettrice** (nom, IBAN, BIC) et la **date d'exécution** (défaut : aujourd'hui + 2 jours ouvrés) ;
   - **filtrer** les paiements par société (`clientId`), période de paie (mois/année de `creeLe`) et matricules (`employeId`, optionnel) ;
   - cocher/décocher les lignes ; voir le nombre et le total ;
  - **Générer** → le XML est généré **à partir du XSD choisi** puis **validé automatiquement** contre ce XSD ;
   - voir l'**aperçu coloré** (numéros de ligne) + « Conforme au XSD ✅ » ou la liste des erreurs ❌ (ligne, colonne, message en clair) ; un clic sur une erreur surligne la ligne ;
  - **Enregistrer et télécharger** devient disponible uniquement pour un aperçu conforme correspondant aux paramètres et à la sélection actuels ; toute modification impose une nouvelle génération ;
  - retrouver et **Revoir** les fichiers dans l'**historique** ; **Retélécharger** est réservé aux fichiers conformes. Les anciens fichiers invalides restent consultables.

## Définitions

- **Paiement éligible à la génération** : transaction au statut `APPROUVEE` sur la période. Les statuts `VALIDEE`, `BROUILLON`, `REJETEE` et `SUPPRIMEE` ne sont pas éligibles. Une sélection directe d'IDs non approuvés est refusée avant génération ou enregistrement ; le statut est vérifié à nouveau lors de l'enregistrement.
- **Non approuvé** : libellé affiché pour le statut existant `BROUILLON`, sans nouveau statut ni modification des données en base. L'approbation est refusée si l'IBAN, le BIC renseigné ou le montant échoue aux contrôles métier ; un BIC absent reste optionnel. L'API renvoie HTTP 422 avec la correction attendue, sans changement de statut ni audit de succès.
- **Période de paie** : mois/année de `creeLe` (aucune colonne ajoutée à `Transaction`).
- **Erreur bloquante par ligne** : IBAN invalide (modulo 97), BIC invalide, montant ≤ 0 — via les contrôles métier existants.

## API (`/api/masse`)

| Méthode | Route | Rôle |
|---|---|---|
| GET | `/filtres` | sociétés, périodes, matricules |
| GET | `/paiements` | paiements filtrés + nombre + total |
| GET | `/xsd` | XSD utilisables (compatibles pain.001) |
| GET | `/xsd/{id}/analyse` | arbre des éléments + contraintes |
| POST | `/apercu` | génère + valide, **sans enregistrer** |
| POST | `/fichiers` | génère + valide + **enregistre uniquement si conforme** (sinon HTTP 422) |
| GET | `/fichiers` | historique (plus récent d'abord) |
| GET | `/fichiers/{id}` | détail (XML + erreurs + paramètres) |
| GET | `/fichiers/{id}/telecharger` | téléchargement conforme (contenu identique ; sinon HTTP 422) |

Droits : `ADMINISTRATEUR`, `GESTIONNAIRE_RH` (génération) ; `VALIDATEUR` en lecture.

## Conformité & sécurité

- Génération via **API DOM** (namespace/variantes selon la version : `001.001.03` → `<BIC>`, date simple ; `001.001.09`, `.10`, `.11` → `<BICFI>`, `<ReqdExctnDt><Dt>`).
- `NbOfTxs` / `CtrlSum` calculés en `BigDecimal` (2 décimales).
- Nettoyage SEPA : translittération des accents, suppression des caractères hors jeu autorisé, troncature au `maxLength`, IBAN en majuscules sans espaces.
- Validation XSD **collectant toutes les erreurs** (ligne, colonne, message traduit).
- Les messages indiquent le champ concerné, la cause et la correction attendue : longueur maximale/minimale, valeurs autorisées, bornes numériques, précision décimale, formats BIC/IBAN/date, attributs et ordre des balises. Les contrôles métier distinguent un IBAN manquant, un mauvais format et une clé incohérente. Les avertissements expliquent pourquoi ils ne bloquent pas la conformité XSD.
- Les diagnostics Xerces d'un même élément et emplacement sont regroupés ; une erreur de type générique ne répète pas la contrainte précise. Les erreurs de valeurs distinctes restent séparées.
- Les balises optionnelles vides de nom et de BIC sont omises uniquement si leur chemin dans le XSD choisi les autorise. Leur omission produit un avertissement non bloquant, rattaché au parent dans le XML. Les champs requis vides et les valeurs fournies mais invalides restent des erreurs ; aucun verdict XSD n'est contourné.
- Dans l'aperçu XML, les erreurs XSD et métier sont soulignées en rouge ; les avertissements et omissions optionnelles sont soulignés en orange. Cliquer sur un diagnostic conserve sa couleur.
- Les erreurs métier et les avertissements (doublon potentiel) sont retournés séparément dans `erreursControleMetier` et `avertissementsControleMetier`, puis affichés en rouge et orange. Le contrôle IBAN modulo 97 conserve les chiffres lors de la conversion ; l'IBAN d'EMP001 est reconnu comme valide.
- Le serveur revalide le XML lors de l'enregistrement, indépendamment du verdict de l'aperçu. La conformité des fichiers historiques est celle enregistrée à leur génération. Les avertissements métier ne sont pas transformés en erreurs XSD.
- Sécurité XXE conservée (doctype interdit, DTD/schémas externes bloqués).

## Persistance

Nouvelle table `sepa_fichiers_generes` (historique). Le contenu enregistré n'est jamais modifié : un re-téléchargement renvoie exactement le même fichier. Aucune table existante n'est modifiée ; aucun statut de transaction n'est changé.

## Tests

`XsdPain001ReaderTest`, `MassPaymentXmlGeneratorTest` (dont **XML valide contre le XSD officiel pain.001.001.03**, formats `.09`, `.10`, `.11`), `XsdErrorCollectorTest` (regroupement, positions et messages français), `MassPaymentServiceTest` (filtres, distinction erreurs/avertissements, validation et export), `BusinessControlServiceTest` (IBAN EMP001, normalisation et clés invalides). Le XSD officiel est dans `backend/src/test/resources/xsd/pain.001.001.03.xsd`.
