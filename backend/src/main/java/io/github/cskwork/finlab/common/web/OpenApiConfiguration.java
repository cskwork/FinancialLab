package io.github.cskwork.finlab.common.web;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    private static final String BEARER_AUTH = "bearerAuth";

    @Bean
    OpenAPI finLabOpenApi() {
        return new OpenAPI()
            .info(new Info().title("FinLab API").version("v1")
                .description("Portfolio ledger, holdings, goals, plans and sample-market-data analytics. "
                    + "Authenticate with email and password, then supply the returned access token as a bearer token."))
            .components(new Components().addSecuritySchemes(BEARER_AUTH,
                new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                    .description("Short-lived RS256 access token returned by POST /v1/auth/login")))
            .addSecurityItem(new SecurityRequirement().addList(BEARER_AUTH));
    }

    @Bean
    OpenApiCustomizer publicLoginOperation() {
        return openApi -> {
            if (openApi.getPaths() == null) return;
            var login = openApi.getPaths().get("/v1/auth/login");
            if (login != null && login.getPost() != null) {
                // An explicit empty list overrides the global bearer requirement.
                login.getPost().setSecurity(List.of());
            }
        };
    }
}
