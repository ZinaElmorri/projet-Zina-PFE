package com.zina.sepa.face;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Gestion du visage de référence de l'utilisateur connecté. */
@RestController
@RequestMapping("/api/face/me")
public class FaceController {

    public record FaceImageRequest(@NotBlank @Size(max = 6_000_000) String image) {}

    private final FaceService faceService;

    public FaceController(FaceService faceService) {
        this.faceService = faceService;
    }

    @GetMapping
    public Map<String, Boolean> statut(Authentication auth) {
        return Map.of("enregistre", faceService.estEnregistre(auth.getName()));
    }

    @PutMapping
    public Map<String, Boolean> enregistrer(@Valid @RequestBody FaceImageRequest request, Authentication auth) {
        faceService.enregistrer(auth.getName(), request.image());
        return Map.of("enregistre", true);
    }

    @DeleteMapping
    public ResponseEntity<Void> supprimer(Authentication auth) {
        faceService.supprimer(auth.getName());
        return ResponseEntity.noContent().build();
    }
}
