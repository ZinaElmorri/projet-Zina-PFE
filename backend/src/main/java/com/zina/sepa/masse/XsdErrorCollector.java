package com.zina.sepa.masse;

import com.zina.sepa.masse.dto.MasseDto.ErreurXsd;
import org.springframework.stereotype.Component;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
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

    public List<ErreurXsd> valider(String xml, String xsd) {
        List<ErreurXsd> erreurs = new ArrayList<>();
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
                    erreurs.add(convertir(e));
                }

                @Override
                public void fatalError(SAXParseException e) {
                    erreurs.add(convertir(e));
                }
            });
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXParseException e) {
            erreurs.add(convertir(e));
        } catch (Exception e) {
            erreurs.add(new ErreurXsd(0, 0, "XSD invalide ou illisible : " + e.getMessage(), ""));
        }
        return erreurs;
    }

    private ErreurXsd convertir(SAXParseException e) {
        String brut = e.getMessage() == null ? "" : e.getMessage();
        String element = extraire(ELEMENT, brut);
        return new ErreurXsd(e.getLineNumber(), e.getColumnNumber(), traduire(brut, element), element);
    }

    private String traduire(String brut, String element) {
        String cible = element.isEmpty() ? "" : " pour <" + element + ">";
        if (brut.contains("cvc-maxLength-valid")) {
            String max = extraire(MAXLEN, brut);
            return "Valeur trop longue" + cible + (max.isEmpty() ? "" : " (max " + max + " caractères)") + ".";
        }
        if (brut.contains("cvc-minLength-valid")) {
            String min = extraire(MINLEN, brut);
            return "Valeur trop courte" + cible + (min.isEmpty() ? "" : " (min " + min + " caractères)") + ".";
        }
        if (brut.contains("cvc-pattern-valid")) {
            return "Format non conforme au XSD" + cible + " (caractères ou motif non autorisés).";
        }
        if (brut.contains("cvc-enumeration-valid") || brut.contains("cvc-datatype-valid")) {
            return "Valeur non autorisée" + cible + " par le XSD.";
        }
        if (brut.contains("cvc-complex-type") || brut.contains("cvc-model-group")) {
            String attendu = extraire(ATTENDU, brut);
            String debut = element.isEmpty() ? "" : " à partir de <" + element + ">";
            return "Élément manquant ou mal ordonné" + debut
                    + (attendu.isEmpty() ? "" : " ; attendu : " + attendu) + ".";
        }
        return brut;
    }

    private String extraire(Pattern p, String texte) {
        Matcher m = p.matcher(texte);
        return m.find() ? m.group(1) : "";
    }
}
