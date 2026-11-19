package com.tradingassistant.platform.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the bearer-token scheme used by every authenticated endpoint so the
 * generated OpenAPI description and Swagger UI can call the API directly.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI tradingAssistantOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Trading Assistant API")
                        .version("v1")
                        .description("""
                                REST surface over the Trading Assistant analysis engine.

                                The API describes market, option, strategy, qualification,
                                risk and execution state. It never recommends, predicts or
                                places a trade on its own: the human user decides and the
                                caller supplies the order direction explicitly.
                                """)
                        .contact(new Contact().name("Trading Assistant")))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
