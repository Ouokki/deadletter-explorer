package com.dle.dlq.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Configuration
public class CorsConfig {

    @Bean
    public CorsWebFilter corsWebFilter(
            @Value("${dle.cors.allowed-origins:http://localhost:5173}") String allowedOrigins,
            @Value("${dle.cors.allowed-origin-patterns:}") String allowedOriginPatterns // e.g. https://*.example.com
    ) {
        List<String> origins = splitCsv(allowedOrigins);
        List<String> originPatterns = splitCsv(allowedOriginPatterns);

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);

        if (!originPatterns.isEmpty()) {
            config.setAllowedOriginPatterns(originPatterns);
        } else {
            config.setAllowedOrigins(origins);
        }

        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "Accept", "Origin", "X-Requested-With"
        ));
        config.setExposedHeaders(List.of(
                "Location", "Content-Disposition", "X-Request-Id"
        ));
        config.setMaxAge(3600L); // seconds

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        log.info("CORS init | allowCredentials={}, origins={}, originPatterns={}, methods={}, headers={}, exposed={}, maxAge={}",
                config.getAllowCredentials(), origins, originPatterns, config.getAllowedMethods(),
                config.getAllowedHeaders(), config.getExposedHeaders(), config.getMaxAge());

        return new CorsWebFilter(source);
    }

    private static List<String> splitCsv(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}
