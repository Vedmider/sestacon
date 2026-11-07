package io.github.vedmider.sestacon.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI ragOpenApi(@Value("${spring.application.name:sestacon}") String appName) {
        return new OpenAPI().info(new Info()
                .title("RAG App API")
                .description("""
                        Retrieval-Augmented Generation service. Upload documents, then ask
                        questions answered from them with source attribution. See /api/ingest
                        and /api/chat.""")
                .version("0.1.0")
                .license(new License().name("Apache-2.0")));
    }
}
