package com.zina.sepa.masse;

import com.zina.sepa.masse.dto.MasseDto.AnalyseXsd;
import com.zina.sepa.masse.dto.MasseDto.ElementXsd;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lit un XSD pain.001 : namespace, version, ordre des éléments de {@code CstmrCdtTrfInitn}
 * (xs:sequence) et contraintes (minOccurs, maxLength, pattern, enumeration…).
 *
 * <p>Lecture seule, XXE désactivé (doctype interdit, entités externes bloquées).</p>
 */
@Component
public class XsdPain001Reader {

    private static final String XS = "http://www.w3.org/2001/XMLSchema";
    private static final Pattern VERSION = Pattern.compile("pain\\.(\\d{3}\\.\\d{3}\\.\\d{2})");
    private static final int MAX_DEPTH = 12;
    private static final int MAX_NODES = 600;

    public record MetaXsd(String version, String namespace, boolean compatible) {}

    public MetaXsd meta(String xsd) {
        if (xsd == null || xsd.isBlank()) {
            return new MetaXsd(null, null, false);
        }
        boolean compatible = xsd.contains("CstmrCdtTrfInitn") && xsd.contains("pain.001");
        String namespace = null;
        try {
            Element schema = parse(xsd).getDocumentElement();
            namespace = schema.getAttribute("targetNamespace");
        } catch (Exception e) {
            Matcher m = Pattern.compile("targetNamespace\\s*=\\s*\"([^\"]+)\"").matcher(xsd);
            if (m.find()) {
                namespace = m.group(1);
            }
        }
        return new MetaXsd(version(namespace, xsd), namespace, compatible);
    }

    public AnalyseXsd analyser(String xsd) {
        if (xsd == null || xsd.isBlank() || !xsd.contains("CstmrCdtTrfInitn")) {
            throw new IllegalArgumentException("Le schéma XSD est vide ou ne contient pas la structure de virements SEPA <CstmrCdtTrfInitn>. Importez ou sélectionnez un schéma pain.001 complet.");
        }
        Document doc = parse(xsd);
        Element schema = doc.getDocumentElement();
        String namespace = schema.getAttribute("targetNamespace");
        String version = version(namespace, xsd);

        Map<String, Element> complexTypes = indexer(schema, "complexType");
        Map<String, Element> simpleTypes = indexer(schema, "simpleType");
        Map<String, Element> topElements = indexerTopElements(schema);

        Element cstmr = trouverCstmr(schema, topElements, complexTypes);
        if (cstmr == null) {
            throw new IllegalArgumentException("La structure de virements SEPA <CstmrCdtTrfInitn> n'est pas définie dans le schéma. Vérifiez que le fichier importé est un schéma pain.001 complet, puis sélectionnez-le à nouveau.");
        }

        int[] compteur = {0};
        ElementXsd racine = fromElement(cstmr, "", complexTypes, simpleTypes, new HashSet<>(), compteur, 0);
        return new AnalyseXsd(version, namespace, compter(racine), compterObligatoires(racine), racine);
    }

    // ------------------------------------------------------------------

    private ElementXsd fromElement(Element el, String parentPath, Map<String, Element> complex,
                                   Map<String, Element> simple, Set<String> typesSurChemin,
                                   int[] compteur, int depth) {
        compteur[0]++;
        String nom = attr(el, "name");
        if (nom.isEmpty()) {
            nom = attr(el, "ref");
        }
        String chemin = parentPath.isEmpty() ? nom : parentPath + "/" + nom;
        boolean obligatoire = !"0".equals(minOccurs(el));
        String max = attr(el, "maxOccurs");
        boolean repetable = "unbounded".equals(max) || (!max.isEmpty() && !"1".equals(max));

        String typeNom = attr(el, "type");
        List<ElementXsd> enfants = new ArrayList<>();
        Integer maxLength = null, minLength = null, fractionDigits = null, totalDigits = null;
        String pattern = null, typeAffiche = typeNom;
        List<String> valeurs = new ArrayList<>();

        if (depth < MAX_DEPTH && compteur[0] < MAX_NODES) {
            Element typeComplexe = null;
            Element typeSimple = null;

            if (!typeNom.isEmpty()) {
                String local = sansPrefixe(typeNom);
                if (complex.containsKey(local)) {
                    typeComplexe = complex.get(local);
                } else if (simple.containsKey(local)) {
                    typeSimple = simple.get(local);
                }
            } else {
                typeComplexe = premierEnfant(el, "complexType");
                typeSimple = premierEnfant(el, "simpleType");
            }

            if (typeSimple != null) {
                Facettes f = facettes(typeSimple);
                maxLength = f.maxLength; minLength = f.minLength; pattern = f.pattern;
                fractionDigits = f.fractionDigits; totalDigits = f.totalDigits;
                valeurs = f.enumeration; typeAffiche = f.base != null ? f.base : typeAffiche;
            } else if (typeComplexe != null) {
                String cle = attr(typeComplexe, "name");
                boolean cycle = !cle.isEmpty() && !typesSurChemin.add(cle);
                if (!cycle) {
                    // Contenu simple (ex. montant avec attribut Ccy) => feuille avec facettes.
                    Element simpleContent = premierDescendant(typeComplexe, "simpleContent");
                    Element restriction = premierDescendant(typeComplexe, "restriction");
                    if (simpleContent != null && restriction != null && premierDescendant(restriction, "enumeration") != null) {
                        Facettes f = facettesDe(restriction);
                        valeurs = f.enumeration; pattern = f.pattern; typeAffiche = f.base;
                    } else {
                        for (Element enfant : elementsDeSequence(typeComplexe)) {
                            enfants.add(fromElement(enfant, chemin, complex, simple,
                                    new HashSet<>(typesSurChemin), compteur, depth + 1));
                        }
                    }
                    if (!cle.isEmpty()) {
                        typesSurChemin.remove(cle);
                    }
                }
            }
        }

        return new ElementXsd(nom, chemin, obligatoire, repetable, typeAffiche,
                maxLength, minLength, pattern, fractionDigits, totalDigits,
                valeurs.isEmpty() ? null : valeurs, enfants.isEmpty() ? null : enfants);
    }

    private record Facettes(String base, Integer maxLength, Integer minLength, String pattern,
                            Integer fractionDigits, Integer totalDigits, List<String> enumeration) {}

    private Facettes facettes(Element simpleType) {
        Element restriction = premierEnfant(simpleType, "restriction");
        if (restriction == null) {
            return new Facettes(null, null, null, null, null, null, List.of());
        }
        return facettesDe(restriction);
    }

    private Facettes facettesDe(Element restriction) {
        String base = sansPrefixe(attr(restriction, "base"));
        Integer maxLength = entier(restriction, "maxLength");
        Integer minLength = entier(restriction, "minLength");
        Integer fractionDigits = entier(restriction, "fractionDigits");
        Integer totalDigits = entier(restriction, "totalDigits");
        String pattern = valeurFacette(restriction, "pattern");
        List<String> enumeration = new ArrayList<>();
        for (Element e : enfantsDirects(restriction, "enumeration")) {
            enumeration.add(attr(e, "value"));
        }
        return new Facettes(base, maxLength, minLength, pattern, fractionDigits, totalDigits, enumeration);
    }

    private Element trouverCstmr(Element schema, Map<String, Element> topElements, Map<String, Element> complex) {
        // 1) Élément top-level "Document" -> son type (complexType) -> élément CstmrCdtTrfInitn.
        Element document = topElements.get("Document");
        if (document != null) {
            Element typeDoc = typeComplexe(document, complex);
            if (typeDoc != null) {
                for (Element e : elementsDeSequence(typeDoc)) {
                    if ("CstmrCdtTrfInitn".equals(attr(e, "name"))) {
                        return e;
                    }
                }
            }
        }
        // 2) Repli : premier élément nommé CstmrCdtTrfInitn où qu'il soit.
        NodeList elements = schema.getElementsByTagNameNS(XS, "element");
        for (int i = 0; i < elements.getLength(); i++) {
            Element e = (Element) elements.item(i);
            if ("CstmrCdtTrfInitn".equals(attr(e, "name"))) {
                return e;
            }
        }
        return null;
    }

    private Element typeComplexe(Element el, Map<String, Element> complex) {
        String type = sansPrefixe(attr(el, "type"));
        if (!type.isEmpty() && complex.containsKey(type)) {
            return complex.get(type);
        }
        return premierEnfant(el, "complexType");
    }

    private List<Element> elementsDeSequence(Element complexType) {
        // sequence directe ou via complexContent/extension.
        Element sequence = premierDescendant(complexType, "sequence");
        List<Element> result = new ArrayList<>();
        if (sequence != null) {
            for (Node n = sequence.getFirstChild(); n != null; n = n.getNextSibling()) {
                if (n.getNodeType() == Node.ELEMENT_NODE && estXs(n, "element")) {
                    result.add((Element) n);
                }
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Utilitaires DOM

    private Map<String, Element> indexer(Element schema, String localName) {
        Map<String, Element> map = new HashMap<>();
        NodeList list = schema.getElementsByTagNameNS(XS, localName);
        for (int i = 0; i < list.getLength(); i++) {
            Element e = (Element) list.item(i);
            String nom = attr(e, "name");
            if (!nom.isEmpty() && !map.containsKey(nom)) {
                map.put(nom, e);
            }
        }
        return map;
    }

    private Map<String, Element> indexerTopElements(Element schema) {
        Map<String, Element> map = new HashMap<>();
        for (Node n = schema.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && estXs(n, "element")) {
                Element e = (Element) n;
                String nom = attr(e, "name");
                if (!nom.isEmpty()) {
                    map.put(nom, e);
                }
            }
        }
        return map;
    }

    private Element premierEnfant(Element parent, String localName) {
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && estXs(n, localName)) {
                return (Element) n;
            }
        }
        return null;
    }

    private Element premierDescendant(Element parent, String localName) {
        NodeList list = parent.getElementsByTagNameNS(XS, localName);
        return list.getLength() > 0 ? (Element) list.item(0) : null;
    }

    private List<Element> enfantsDirects(Element parent, String localName) {
        List<Element> result = new ArrayList<>();
        for (Node n = parent.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE && estXs(n, localName)) {
                result.add((Element) n);
            }
        }
        return result;
    }

    private boolean estXs(Node n, String localName) {
        return XS.equals(n.getNamespaceURI()) && localName.equals(n.getLocalName());
    }

    private String attr(Element e, String name) {
        String v = e.getAttribute(name);
        return v == null ? "" : v.trim();
    }

    private String minOccurs(Element e) {
        String v = attr(e, "minOccurs");
        return v.isEmpty() ? "1" : v;
    }

    private Integer entier(Element restriction, String facette) {
        Element f = premierEnfant(restriction, facette);
        if (f == null) {
            return null;
        }
        try {
            return Integer.valueOf(attr(f, "value"));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String valeurFacette(Element restriction, String facette) {
        Element f = premierEnfant(restriction, facette);
        return f == null ? null : attr(f, "value");
    }

    private String sansPrefixe(String qname) {
        if (qname == null) {
            return "";
        }
        int i = qname.indexOf(':');
        return i >= 0 ? qname.substring(i + 1) : qname;
    }

    private String version(String namespace, String xsd) {
        String source = namespace != null ? namespace : (xsd != null ? xsd : "");
        Matcher m = VERSION.matcher(source);
        return m.find() ? m.group(1) : "inconnue";
    }

    private int compter(ElementXsd e) {
        int n = 1;
        if (e.enfants() != null) {
            for (ElementXsd enfant : e.enfants()) {
                n += compter(enfant);
            }
        }
        return n;
    }

    private int compterObligatoires(ElementXsd e) {
        int n = e.obligatoire() ? 1 : 0;
        if (e.enfants() != null) {
            for (ElementXsd enfant : e.enfants()) {
                n += compterObligatoires(enfant);
            }
        }
        return n;
    }

    private Document parse(String xsd) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder().parse(new org.xml.sax.InputSource(new StringReader(xsd)));
        } catch (Exception e) {
            throw new IllegalArgumentException("Schéma XSD illisible : vérifiez les balises, les attributs et l'absence de DOCTYPE dans le fichier, puis importez un schéma XML bien formé.", e);
        }
    }
}
