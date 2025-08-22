package com.dle.dlq.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${dle.cors.allowedOrigins:http://localhost:5173}") String allowedOrigins) {

        List<String> origins = Arrays.asList(allowedOrigins.split(","));
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
        config.setAllowedOrigins(origins);
        config.addAllowedHeader("*");

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        log.info(
                "CORS configuration initialized: allowedOrigins={}, allowCredentials={}, allowedHeaders=*, allowedMethods=*",
                origins, config.getAllowCredentials());

        return new CorsWebFilter(source);
    }
}
