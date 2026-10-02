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
   - **Aperçu** → le XML est généré **à partir du XSD choisi** puis **validé automatiquement** contre ce XSD ;
   - voir l'**aperçu coloré** (numéros de ligne) + « Conforme au XSD ✅ » ou la liste des erreurs ❌ (ligne, colonne, message en clair) ; un clic sur une erreur surligne la ligne ;
   - **Générer et enregistrer** puis **Télécharger** le XML ;
   - retrouver, **Revoir** et **Retélécharger** les fichiers dans l'**historique**.

## Définitions

- **Fiche « générée »** : transaction au statut `APPROUVEE` (option `VALIDEE`) sur la période. Jamais `BROUILLON` / `REJETEE` / `SUPPRIMEE`.
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
| POST | `/fichiers` | génère + valide + **enregistre** (historique) |
| GET | `/fichiers` | historique (plus récent d'abord) |
| GET | `/fichiers/{id}` | détail (XML + erreurs + paramètres) |
| GET | `/fichiers/{id}/telecharger` | téléchargement (contenu identique) |

Droits : `ADMINISTRATEUR`, `GESTIONNAIRE_RH` (génération) ; `VALIDATEUR` en lecture.

## Conformité & sécurité

- Génération via **API DOM** (namespace/variantes selon la version : `001.001.03` → `<BIC>`, date simple ; `001.001.09` → `<BICFI>`, `<ReqdExctnDt><Dt>`).
- `NbOfTxs` / `CtrlSum` calculés en `BigDecimal` (2 décimales).
- Nettoyage SEPA : translittération des accents, suppression des caractères hors jeu autorisé, troncature au `maxLength`, IBAN en majuscules sans espaces.
- Validation XSD **collectant toutes les erreurs** (ligne, colonne, message traduit).
- Sécurité XXE conservée (doctype interdit, DTD/schémas externes bloqués).

## Persistance

Nouvelle table `sepa_fichiers_generes` (historique). Le contenu enregistré n'est jamais modifié : un re-téléchargement renvoie exactement le même fichier. Aucune table existante n'est modifiée ; aucun statut de transaction n'est changé.

## Tests

`XsdPain001ReaderTest`, `MassPaymentXmlGeneratorTest` (dont **XML valide contre le XSD officiel pain.001.001.03**), `XsdErrorCollectorTest` (2 erreurs collectées), `MassPaymentServiceTest` (filtres, statut par défaut, retéléchargement identique). Le XSD officiel est dans `backend/src/test/resources/xsd/pain.001.001.03.xsd`.
