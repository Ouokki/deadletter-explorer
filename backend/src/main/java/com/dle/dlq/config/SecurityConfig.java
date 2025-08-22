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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/dlq/**").hasAnyRole("viewer", "triager", "replayer")
                        .requestMatchers(HttpMethod.POST, "/api/dlq/replay").hasAnyRole("triager", "replayer")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakAuthConverter())));
        return http.build();
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> keycloakAuthConverter() {
        return jwt -> {
            var authorities = new HashSet<GrantedAuthority>();
            // realm roles (optional)
            extractRoles(jwt.getClaimAsMap("realm_access"), "roles")
                    .forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));

            // client roles under resource_access.dle-api.roles
            var ra = jwt.getClaimAsMap("resource_access");
            if (ra != null) {
                var api = (Map<String, Object>) ra.get("dle-api");
                extractRoles(api, "roles").forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
            }
            return new JwtAuthenticationToken(jwt, authorities);
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
