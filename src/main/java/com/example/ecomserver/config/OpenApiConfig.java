package com.example.ecomserver.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI metadata for the REST API.
 *
 * <p>Declares the optional API-key auth (see {@link McpApiKeyAuthFilter}) as a security scheme
 * so the Swagger UI "Authorize" button lets you paste the key and have "Try it out" send it.
 * When {@code ecom.security.api-key} is blank the endpoints are open and the key is ignored.
 */
@Configuration
public class OpenApiConfig {

    private static final String API_KEY_SCHEME = "apiKey";

    @Bean
    public OpenAPI ecommerceOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("India E-commerce API")
                        .version("1.0.0")
                        .description("""
                                REST API over the same product-search, price, rating, and price-history
                                capabilities exposed as MCP tools. Searches Amazon.in, Flipkart, Myntra
                                and Meesho, and tracks price history for products it has seen.

                                The MCP protocol endpoint lives at `/mcp` and is not part of this REST
                                spec; use an MCP client (e.g. claude.ai) for that.""")
                        .license(new License().name("MIT")))
                .components(new Components()
                        .addSecuritySchemes(API_KEY_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("Optional shared secret. Sent as 'Authorization: Bearer <key>'. "
                                        + "Only required when ECOM_SECURITY_API_KEY is configured on the server.")))
                .addSecurityItem(new SecurityRequirement().addList(API_KEY_SCHEME));
    }
}
