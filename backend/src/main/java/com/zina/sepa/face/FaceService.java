package com.zina.sepa.face;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zina.sepa.domain.entity.User;
import com.zina.sepa.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FaceService {

    static final String NON_RECONNU = "Visage non reconnu ou reconnaissance faciale non activée pour ce compte";

    private final FaceClient faceClient;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public FaceService(FaceClient faceClient, UserRepository userRepository, ObjectMapper objectMapper) {
        this.faceClient = faceClient;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    public boolean estEnregistre(String username) {
        return userRepository.findByUsername(username)
                .map(u -> u.getFaceEncoding() != null && !u.getFaceEncoding().isBlank())
                .orElse(false);
    }

    public void enregistrer(String username, String image) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setFaceEncoding(encoder(image));
        userRepository.save(user);
    }

    /** Calcule l'encodage du visage (via le module IA) au format de stockage JSON. */
    public String encoder(String image) {
        List<Double> encoding = faceClient.encode(image);
        try {
            return objectMapper.writeValueAsString(encoding);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Impossible d'enregistrer l'encodage du visage");
        }
    }

    public void supprimer(String username) {
        User user = userRepository.findByUsername(username).orElseThrow();
        user.setFaceEncoding(null);
        userRepository.save(user);
    }

    /** Retourne l'utilisateur si le visage capturé correspond à sa référence enregistrée. */
    public User authentifier(String username, String image) {
        User user = userRepository.findByUsername(username)
                .filter(User::isActif)
                .filter(u -> u.getFaceEncoding() != null && !u.getFaceEncoding().isBlank())
                .orElseThrow(() -> new IllegalArgumentException(NON_RECONNU));

        List<Double> reference;
        try {
            reference = objectMapper.readValue(user.getFaceEncoding(), new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(NON_RECONNU);
        }
        if (!faceClient.verify(image, reference).match()) {
            throw new IllegalArgumentException(NON_RECONNU);
        }
        return user;
    }
}
