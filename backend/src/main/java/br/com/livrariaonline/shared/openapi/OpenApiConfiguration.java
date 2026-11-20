package br.com.livrariaonline.shared.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class OpenApiConfiguration {
    @Bean
    OpenAPI livrariaOpenApi() {
        return new OpenAPI().info(new Info().title("Livraria Online API").version("v1")
                .description("Contrato REST do MVP da Livraria Online. Erros retornam code, message, details e correlationId."));
    }
}
