package com.zina.sepa.face;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Client HTTP vers les endpoints de reconnaissance faciale du module IA.
 * Erreurs fonctionnelles (aucun visage…) -> IllegalArgumentException ; indisponibilité -> IllegalStateException.
 */
@Component
public class FaceClient {

    public record EncodeResponse(List<Double> encoding) {}

    public record VerifyResponse(boolean match, double score, double seuil) {}

    private final RestClient aiRestClient;
    private final ObjectMapper objectMapper;

    public FaceClient(@Qualifier("aiRestClient") RestClient aiRestClient, ObjectMapper objectMapper) {
        this.aiRestClient = aiRestClient;
        this.objectMapper = objectMapper;
    }

    public List<Double> encode(String image) {
        return call("/api/face/encode", Map.of("image", image), EncodeResponse.class).encoding();
    }

    public VerifyResponse verify(String image, List<Double> reference) {
        return call("/api/face/verify", Map.of("image", image, "reference", reference), VerifyResponse.class);
    }

    private <T> T call(String uri, Object body, Class<T> type) {
        try {
            return aiRestClient.post().uri(uri).body(body).retrieve().body(type);
        } catch (HttpClientErrorException.UnprocessableEntity e) {
            throw new IllegalArgumentException(detail(e.getResponseBodyAsString()));
        } catch (RestClientException e) {
            throw new IllegalStateException("Service de reconnaissance faciale indisponible");
        }
    }

    private String detail(String json) {
        try {
            JsonNode node = objectMapper.readTree(json).path("detail");
            return node.isTextual() ? node.asText() : "Image refusée par la reconnaissance faciale";
        } catch (Exception e) {
            return "Image refusée par la reconnaissance faciale";
        }
    }
}
