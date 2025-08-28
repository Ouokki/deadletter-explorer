package com.dle.dlq.config;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import reactor.core.publisher.Mono;

@Configuration
@EnableWebFluxSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .cors(cors -> {
                })
                .csrf(csrf -> csrf.disable())
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance()) // stateless
                .authorizeExchange(auth -> auth
                        .pathMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .pathMatchers(HttpMethod.GET, "/api/dlq/**").hasAnyRole("viewer", "triager", "replayer")
                        .pathMatchers(HttpMethod.POST, "/api/dlq/replay").hasAnyRole("triager", "replayer")
                        .pathMatchers(HttpMethod.GET, "/api/redaction/rules").hasAnyRole("triager", "replayer")
                        .pathMatchers(HttpMethod.PUT, "/api/redaction/rules").hasAnyRole("triager", "replayer")
                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(reactiveJwtAuthenticationConverter())))
                .build();
    }

    private Converter<Jwt, Mono<AbstractAuthenticationToken>> reactiveJwtAuthenticationConverter() {
        return jwt -> {
            var authorities = new HashSet<GrantedAuthority>();

            extractRoles(jwt.getClaimAsMap("realm_access"), "roles")
                    .forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));

            var ra = jwt.getClaimAsMap("resource_access");
            if (ra instanceof Map<?, ?> map && map.get("dle-api") instanceof Map<?, ?> api) {
                @SuppressWarnings("unchecked")
                var apiMap = (Map<String, Object>) api;
                extractRoles(apiMap, "roles")
                        .forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }

            return Mono.just(new JwtAuthenticationToken(jwt, authorities));
        };
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractRoles(Map<String, Object> node, String key) {
        if (node == null)
            return List.of();
        var v = node.get(key);
        return v instanceof Collection<?> c ? c.stream().map(Object::toString).toList() : List.of();
    }
}
