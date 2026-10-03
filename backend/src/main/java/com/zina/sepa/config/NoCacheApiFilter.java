package com.zina.sepa.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Empêche la mise en cache navigateur des réponses de l'API /api/**.
 * Évite que des données supprimées/modifiées restent affichées tant que
 * l'utilisateur n'a pas forcé un rafraîchissement (F5).
 * Ajout uniquement : ne modifie ni la sécurité ni les contrôleurs ;
 * les en-têtes de téléchargement (Content-Disposition) restent intacts.
 */
@Component
public class NoCacheApiFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        // Posé avant la chaîne : les contrôleurs REST ne définissent pas
        // Cache-Control, donc ces en-têtes ne sont pas écrasés.
        response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
        response.setHeader("Pragma", "no-cache");
        response.setHeader("Expires", "0");
        chain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }
}
