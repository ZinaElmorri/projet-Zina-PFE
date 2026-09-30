package com.zina.sepa.service;

import com.zina.sepa.dto.ValidationResult;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Validation XML : bonne-formation, conformité à un XSD, et présence des champs obligatoires.
 */
@Service
public class ValidationService {

    /**
     * Valide un document XML contre un schéma XSD fourni.
     */
    public ValidationResult validerContreXsd(String xml, String xsd) {
        List<String> erreurs = new ArrayList<>();
        try {
            SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            // Sécurité XXE
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            Schema schema = factory.newSchema(new StreamSource(new StringReader(xsd)));
            Validator validator = schema.newValidator();
            validator.validate(new StreamSource(new StringReader(xml)));
        } catch (SAXException e) {
            erreurs.add("Non conforme au XSD : " + e.getMessage());
        } catch (Exception e) {
            erreurs.add("Erreur de validation : " + e.getMessage());
        }
        return new ValidationResult(erreurs.isEmpty(), erreurs, List.of());
    }

    /**
     * Vérifie uniquement que le XML est bien formé (syntaxe).
     */
    public ValidationResult validerSyntaxe(String xml) {
        List<String> erreurs = new ArrayList<>();
        try {
            var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.newDocumentBuilder().parse(
                    new org.xml.sax.InputSource(new StringReader(xml)));
        } catch (Exception e) {
            erreurs.add("XML mal formé : " + e.getMessage());
        }
        return new ValidationResult(erreurs.isEmpty(), erreurs, List.of());
    }
}
