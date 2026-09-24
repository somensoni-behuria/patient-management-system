package com.pm.patient.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI patientServiceOpenAPI() {
        return new OpenAPI().info(new Info()
                .title("Patient Service API")
                .version("1.0.0")
                .description("Manages patients; creates billing accounts via gRPC and "
                        + "publishes patient events to Kafka."));
    }
}
