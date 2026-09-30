package com.zina.sepa.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI sepaOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("API Plateforme SEPA Intelligente")
                .description("Génération, validation et contrôle des virements SEPA ISO 20022")
                .version("0.1.0"));
    }
}
