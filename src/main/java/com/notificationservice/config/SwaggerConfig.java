package com.notificationservice.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

	@Bean
	public OpenAPI openAPI() {

		return new OpenAPI()

				.info(new Info()
						.title("Notification Service API")
						.description("API documentation with JWT authentication")
						.version("1.0"))

				.addSecurityItem(new SecurityRequirement().addList("BearerAuth"))
				.components(new Components()
						.addSecuritySchemes("BearerAuth",
								new SecurityScheme()

										.name("Authorization")

										.type(SecurityScheme.Type.HTTP)

										.scheme("bearer")

										.bearerFormat("JWT")
						)
				);
	}
}
