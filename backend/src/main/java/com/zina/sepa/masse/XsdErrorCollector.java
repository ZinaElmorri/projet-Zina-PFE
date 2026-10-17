package com.zina.sepa.masse;

import com.zina.sepa.masse.dto.MasseDto.ErreurXsd;
import org.springframework.stereotype.Component;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Valide un XML contre un XSD en collectant TOUTES les erreurs (pas seulement la première),
 * avec ligne, colonne, élément concerné et message traduit en clair.
 *
 * <p>Sécurité XXE : doctype interdit, accès aux entités/DTD/schémas externes bloqué.</p>
 */
@Component
public class XsdErrorCollector {

    private static final Pattern ELEMENT = Pattern.compile("element '([^']+)'");
    private static final Pattern MAXLEN = Pattern.compile("maxLength '([0-9]+)'");
    private static final Pattern MINLEN = Pattern.compile("minLength '([0-9]+)'");
    private static final Pattern ATTENDU = Pattern.compile("One of '\\{([^}]*)\\}' is expected");
    private static final Pattern VALEUR = Pattern.compile("Value '([^']*)'");

    public List<ErreurXsd> valider(String xml, String xsd) {
        List<SAXParseException> diagnostics = new ArrayList<>();
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            Schema schema = factory.newSchema(new StreamSource(new StringReader(xsd)));
            Validator validator = schema.newValidator();
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            validator.setErrorHandler(new ErrorHandler() {
                @Override
                public void warning(SAXParseException e) {
                    // Les avertissements ne bloquent pas la conformité.
                }

                @Override
                public void error(SAXParseException e) {
                    diagnostics.add(e);
                }

                @Override
                public void fatalError(SAXParseException e) {
                    diagnostics.add(e);
                }
            });
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            diagnostics.add(e);
        } catch (Exception e) {
            return List.of(new ErreurXsd(0, 0, "Validation impossible : le XML ou le schéma XSD ne peut pas être lu. Vérifiez que les deux documents sont complets et que le schéma sélectionné correspond à la version du fichier SEPA.", ""));
        }
        Map<String, String> comptes = comptesIban(xml);
        Map<String, ErreurXsd> erreurs = new LinkedHashMap<>();
        for (SAXParseException diagnostic : diagnostics) {
            String brut = message(diagnostic);
            if (brut.startsWith("cvc-type.3.1.3:") && diagnostics.stream().anyMatch(autre ->
                    memePosition(diagnostic, autre) && contrainteDeValeur(message(autre)))) {
                continue;
            }
            String element = extraire(ELEMENT, brut);
            if (element.isEmpty()) {
                element = diagnostics.stream().filter(autre -> memePosition(diagnostic, autre))
                        .map(autre -> extraire(ELEMENT, message(autre)))
                        .filter(nom -> !nom.isEmpty()).findFirst().orElse("");
            }
            element = sansNamespace(element);
                ErreurXsd erreur = new ErreurXsd(diagnostic.getLineNumber(), diagnostic.getColumnNumber(),
                        "IBAN".equals(element) && contrainteDeValeur(brut)
                            ? expliquerIban(brut, comptes.get(diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber()))
                            : traduire(brut, element), element);
                String position = erreur.ligne() + ":" + erreur.colonne() + ":" + erreur.element();
                erreurs.merge(position, erreur, (existante, nouvelle) ->
                    existante.message().contains(nouvelle.message()) ? existante :
                        new ErreurXsd(existante.ligne(), existante.colonne(),
                            existante.message() + " " + nouvelle.message(), existante.element()));
        }
            return new ArrayList<>(erreurs.values());
    }

    private String message(SAXParseException diagnostic) {
        return diagnostic.getMessage() == null ? "" : diagnostic.getMessage();
    }

    private String expliquerIban(String brut, String compte) {
        String cible = compte == null ? "IBAN" : "IBAN du compte " + compte;
        Matcher valeur = VALEUR.matcher(brut);
        if (valeur.find() && valeur.group(1).isBlank()) {
            String correction = "débiteur".equals(compte)
                    ? "Renseignez le champ IBAN débiteur avant de générer le XML."
                    : "bénéficiaire".equals(compte)
                        ? "Renseignez l'IBAN du bénéficiaire dans la fiche du paiement."
                        : "Renseignez l'IBAN du compte concerné avant de générer le XML.";
            return cible + " manquant. " + correction;
        }
        return cible + " au format incorrect. Attendu : deux lettres de pays, deux chiffres de contrôle, "
                + "puis le numéro de compte (lettres et chiffres), sans espaces ni caractères spéciaux. "
                + "Exemple : FR7630006000011234567890189.";
    }

    private Map<String, String> comptesIban(String xml) {
        Map<String, String> comptes = new LinkedHashMap<>();
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try {
            var lecteur = factory.createXMLStreamReader(new StringReader(xml));
            String compte = null;
            try {
                while (lecteur.hasNext()) {
                    int evenement = lecteur.next();
                    if (evenement == XMLStreamConstants.START_ELEMENT) {
                        if (lecteur.getLocalName().equals("DbtrAcct")) compte = "débiteur";
                        if (lecteur.getLocalName().equals("CdtrAcct")) compte = "bénéficiaire";
                    } else if (evenement == XMLStreamConstants.END_ELEMENT) {
                        if (lecteur.getLocalName().equals("IBAN") && compte != null) {
                            comptes.put(lecteur.getLocation().getLineNumber() + ":" + lecteur.getLocation().getColumnNumber(), compte);
                        }
                        if (lecteur.getLocalName().equals("DbtrAcct") || lecteur.getLocalName().equals("CdtrAcct")) {
                            compte = null;
                        }
                    }
                }
            } finally {
                lecteur.close();
            }
        } catch (Exception exception) {
            return Map.of();
        }
        return comptes;
    }

    private boolean memePosition(SAXParseException premiere, SAXParseException seconde) {
        return premiere.getLineNumber() == seconde.getLineNumber()
                && premiere.getColumnNumber() == seconde.getColumnNumber();
    }

    private boolean contrainteDeValeur(String message) {
        return message.matches("(?s)cvc-(maxLength|minLength|length|pattern|enumeration|datatype|" +
                "fractionDigits|totalDigits|minInclusive|maxInclusive|minExclusive|maxExclusive)-valid.*");
    }

    private String sansNamespace(String nom) {
        return nom.replaceAll("\"[^\"]*\":", "")
                .replaceAll("\\{[^}]*\\}(?=[A-Za-z_])", "").replace("{", "").replace("}", "");
    }

    private String traduire(String brut, String element) {
        String cible = element.isEmpty() ? "Champ concerné" : libelle(element) + " (<" + element + ">)";
        if (brut.contains("cvc-maxLength-valid")) {
            String max = extraire(MAXLEN, brut);
            return cible + " : valeur trop longue. Raccourcissez ce champ"
                    + (max.isEmpty() ? " selon la limite du schéma sélectionné" : " à " + max + " caractères maximum") + ".";
        }
        if (brut.contains("cvc-minLength-valid")) {
            String min = extraire(MINLEN, brut);
            return cible + " : valeur manquante ou trop courte. Renseignez"
                    + (min.isEmpty() ? " une valeur respectant la longueur minimale du schéma" : " au moins " + min + " caractères") + ".";
        }
        if (brut.contains("cvc-length-valid")) {
            String longueur = extraire(Pattern.compile("length '([0-9]+)'"), brut);
            return cible + " : longueur incorrecte. Renseignez exactement " + longueur + " caractères.";
        }
        if (brut.contains("cvc-pattern-valid")) {
            if (element.equals("BIC") || element.equals("BICFI")) {
                return cible + " : format incorrect. Renseignez 8 ou 11 caractères : quatre lettres pour la banque, "
                        + "deux lettres pour le pays, deux lettres ou chiffres pour la localisation et, éventuellement, "
                        + "trois lettres ou chiffres pour l'agence, sans espaces. Exemple : AGRIFRPP.";
            }
            if (element.equals("Ccy")) {
                return cible + " : code devise incorrect. Renseignez trois lettres majuscules, par exemple EUR.";
            }
            String motif = extraire(Pattern.compile("pattern '([^']*)'"), brut);
            return cible + " : format incorrect. Corrigez les caractères ou la structure de la valeur"
                    + (motif.isEmpty() ? " selon le format défini dans le schéma sélectionné" : " pour respecter le format XSD : " + motif) + ".";
        }
        if (brut.contains("cvc-enumeration-valid")) {
            String valeurs = extraire(Pattern.compile("enumeration '\\[([^]]*)\\]'"), brut);
            return cible + " : valeur non autorisée. Choisissez"
                    + (valeurs.isEmpty() ? " une valeur de la liste définie dans le schéma sélectionné" : " parmi : " + valeurs) + ".";
        }
        if (brut.contains("cvc-datatype-valid") || brut.contains("cvc-type.3.1.3")) {
            if (brut.contains("'date'") || element.equals("Dt") || element.equals("ReqdExctnDt")) {
                return cible + " : date invalide. Renseignez une date réelle au format AAAA-MM-JJ, par exemple 2026-10-03.";
            }
            if (brut.contains("'dateTime'") || element.equals("CreDtTm")) {
                return cible + " : date et heure invalides. Utilisez le format AAAA-MM-JJThh:mm:ss, par exemple 2026-10-03T09:30:00.";
            }
            if (brut.contains("'decimal'") || element.equals("InstdAmt") || element.equals("CtrlSum")) {
                return cible + " : montant invalide. Renseignez un nombre avec un point comme séparateur décimal, sans devise ni séparateur de milliers. Exemple : 1500.50.";
            }
            if (brut.contains("'integer'") || element.equals("NbOfTxs")) {
                return cible + " : nombre entier attendu. Supprimez les décimales et les caractères non numériques.";
            }
            return cible + " : valeur incompatible avec le type défini dans le schéma. Vérifiez le format et les caractères de ce champ.";
        }
        if (brut.contains("cvc-fractionDigits-valid") || brut.contains("cvc-totalDigits-valid")) {
            String limite = extraire(Pattern.compile("limited to ([0-9]+)"), brut);
            boolean decimales = brut.contains("cvc-fractionDigits-valid");
            return cible + " : trop de " + (decimales ? "décimales" : "chiffres") + ". "
                    + (decimales ? "Réduisez le nombre de décimales" : "Réduisez le nombre total de chiffres")
                    + (limite.isEmpty() ? " selon la limite du schéma" : " à " + limite + " maximum") + ".";
        }
        if (contrainteDeValeur(brut)) {
            String limite = extraire(Pattern.compile("(?:minInclusive|maxInclusive|minExclusive|maxExclusive) '([^']+)'"), brut);
            String comparaison = brut.contains("minInclusive") ? "supérieure ou égale à "
                    : brut.contains("maxInclusive") ? "inférieure ou égale à "
                    : brut.contains("minExclusive") ? "strictement supérieure à " : "strictement inférieure à ";
            return cible + " : valeur hors de la plage autorisée. Renseignez une valeur "
                    + (limite.isEmpty() ? "respectant les bornes du schéma sélectionné" : comparaison + limite) + ".";
        }
        if (brut.contains("cvc-complex-type.2.3:")) {
            return cible + " : Contenu textuel interdit directement dans cette balise. Placez la valeur dans la balise enfant prévue par le schéma.";
        }
        if (brut.contains("cvc-complex-type.3.2.2")) {
            String attribut = extraire(Pattern.compile("Attribute '([^']+)'"), brut);
            return cible + " : attribut non autorisé" + (attribut.isEmpty() ? "" : " (« " + attribut + " »)")
                    + ". Supprimez cet attribut ou utilisez celui prévu par le schéma sélectionné.";
        }
        if (brut.contains("cvc-complex-type.4")) {
            String attribut = extraire(Pattern.compile("Attribute '([^']+)'"), brut);
            return cible + " : attribut obligatoire manquant" + (attribut.isEmpty() ? "" : " (« " + attribut + " »)")
                    + ". Renseignez cet attribut avant de valider le XML.";
        }
        if (brut.contains("cvc-complex-type") || brut.contains("cvc-model-group")) {
            String attendu = sansNamespace(extraire(ATTENDU, brut));
            return cible + " : balise manquante, inattendue ou placée dans le mauvais ordre"
                    + (attendu.isEmpty() ? ". Vérifiez les balises enfants et leur ordre dans le schéma sélectionné."
                        : " ; attendu : " + attendu + ". Ajoutez la balise attendue ou replacez les balises dans l'ordre défini par le schéma.");
        }
        if (brut.contains("cvc-elt.1")) {
            return cible + " : balise inconnue du schéma sélectionné. Vérifiez le nom de la balise, le namespace et la version pain.001 du XML et du XSD.";
        }
        if (brut.startsWith("cvc-")) {
            return cible + " : le contenu ne respecte pas le schéma sélectionné. Vérifiez la valeur, les attributs et les balises enfants de cet élément.";
        }
        if (brut.contains("matching end-tag")) {
            String balise = extraire(Pattern.compile("element type \"([^\"]+)\""), brut);
            return "XML mal formé : balise de fermeture manquante ou incorrecte"
                    + (balise.isEmpty() ? "" : " pour <" + balise + ">")
                    + ". Fermez chaque balise avec le même nom et dans l'ordre inverse de son ouverture.";
        }
        return "XML ou XSD mal formé : vérifiez, à la ligne indiquée, les balises de fermeture, les guillemets des attributs et les caractères réservés (& doit être écrit &amp;).";
    }

    private String libelle(String element) {
        return switch (element) {
            case "Nm" -> "Nom";
            case "BIC", "BICFI" -> "Code BIC/SWIFT";
            case "InstdAmt" -> "Montant du virement";
            case "CtrlSum" -> "Somme totale des virements";
            case "NbOfTxs" -> "Nombre de virements";
            case "MsgId" -> "Identifiant du fichier";
            case "PmtInfId" -> "Identifiant du lot de paiements";
            case "EndToEndId" -> "Référence du virement";
            case "ReqdExctnDt", "Dt" -> "Date d'exécution";
            case "CreDtTm" -> "Date et heure de création";
            case "Ccy" -> "Devise";
            case "ChrgBr" -> "Répartition des frais bancaires";
            case "PmtMtd" -> "Mode de paiement";
            case "SvcLvl" -> "Niveau de service bancaire";
            case "Ustrd" -> "Libellé du virement";
            case "Dbtr" -> "Débiteur";
            case "Cdtr" -> "Bénéficiaire";
            case "DbtrAcct" -> "Compte du débiteur";
            case "CdtrAcct" -> "Compte du bénéficiaire";
            default -> "Élément " + element;
        };
    }

    private String extraire(Pattern p, String texte) {
        Matcher m = p.matcher(texte);
        return m.find() ? m.group(1) : "";
    }
}
