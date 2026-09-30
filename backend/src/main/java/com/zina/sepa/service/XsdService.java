package com.zina.sepa.service;

import com.zina.sepa.domain.entity.XsdSchema;
import com.zina.sepa.repository.XsdSchemaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Gestion des fichiers XSD : import, versionnage et analyse dynamique de la structure.
 */
@Service
public class XsdService {

    private final XsdSchemaRepository repository;

    public XsdService(XsdSchemaRepository repository) {
        this.repository = repository;
    }

    public XsdSchema importer(MultipartFile fichier, String username) throws IOException {
        String contenu = new String(fichier.getBytes(), StandardCharsets.UTF_8);
        String nom = fichier.getOriginalFilename();

        int version = repository.findFirstByNomOrderByVersionDesc(nom)
                .map(s -> s.getVersion() + 1)
                .orElse(1);

        XsdSchema schema = XsdSchema.builder()
                .nom(nom)
                .version(version)
                .contenu(contenu)
                .importePar(username)
                .build();
        return repository.save(schema);
    }

    public List<XsdSchema> findAll() {
        return repository.findAll();
    }

    public Optional<XsdSchema> findById(Long id) {
        return repository.findById(id);
    }

    /**
     * Analyse dynamique : extrait la liste des éléments décrits dans le schéma XSD.
     */
    public List<String> analyserElements(Long id) {
        XsdSchema schema = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schéma introuvable"));
        List<String> elements = new ArrayList<>();
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            // Sécurité XXE : désactiver les entités externes.
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            var doc = builder.parse(new ByteArrayInputStream(
                    schema.getContenu().getBytes(StandardCharsets.UTF_8)));

            NodeList nodes = doc.getElementsByTagNameNS(
                    "http://www.w3.org/2001/XMLSchema", "element");
            for (int i = 0; i < nodes.getLength(); i++) {
                Element el = (Element) nodes.item(i);
                String name = el.getAttribute("name");
                if (!name.isBlank()) {
                    elements.add(name);
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("XSD illisible : " + e.getMessage(), e);
        }
        return elements;
    }
}
