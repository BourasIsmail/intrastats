package ma.entraide.intranet.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ma.entraide.intranet.common.AppProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** En-tête exigé sur les requêtes qui modifient des données (défense CSRF en plus de SameSite=Strict). */
    public static final String REQUIRED_HEADER = "X-Requested-With";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AppProperties props) throws Exception {
        BearerTokenResolver resolver = tokenResolver(props.cookie().name());
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(h -> h.frameOptions(f -> f.deny()))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/api/auth/login", "/api/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o
                        .bearerTokenResolver(resolver)
                        .jwt(j -> j.jwtAuthenticationConverter(authenticationConverter())))
                .addFilterBefore(new RequiredHeaderFilter(), BasicAuthenticationFilter.class);
        return http.build();
    }

    /** Lit le jeton dans le cookie HttpOnly, sinon dans l'en-tête Authorization (outils, tests). */
    private BearerTokenResolver tokenResolver(String cookieName) {
        DefaultBearerTokenResolver fallback = new DefaultBearerTokenResolver();
        return request -> {
            if (request.getCookies() != null) {
                for (Cookie c : request.getCookies()) {
                    if (cookieName.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                        return c.getValue();
                    }
                }
            }
            return fallback.resolve(request);
        };
    }

    private Converter<Jwt, AbstractAuthenticationToken> authenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + jwt.getClaimAsString("role")));
            List<String> droits = jwt.getClaimAsStringList("droits");
            if (droits != null) {
                droits.forEach(d -> authorities.add(new SimpleGrantedAuthority(d)));
            }
            return authorities;
        });
        return converter;
    }

    @Bean
    JwtEncoder jwtEncoder(AppProperties props) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey(props)));
    }

    @Bean
    JwtDecoder jwtDecoder(AppProperties props) {
        return NimbusJwtDecoder.withSecretKey(secretKey(props)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private SecretKey secretKey(AppProperties props) {
        return new SecretKeySpec(props.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /**
     * bcrypt pour tout nouveau mot de passe ; l'ancien format MySQL PASSWORD() reste lisible
     * sous le préfixe {mysql} et est remplacé par du bcrypt à la première connexion réussie.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = Map.of(
                "bcrypt", new BCryptPasswordEncoder(12),
                "mysql", new LegacyMysqlPasswordEncoder());
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }

    /** Refuse les requêtes qui modifient des données sans l'en-tête X-Requested-With. */
    static class RequiredHeaderFilter extends OncePerRequestFilter {
        private static final Set<String> SAFE = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            boolean api = request.getRequestURI().startsWith("/api/");
            if (api && !SAFE.contains(request.getMethod()) && request.getHeader(REQUIRED_HEADER) == null) {
                response.setStatus(HttpStatus.FORBIDDEN.value());
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.getWriter().write("{\"status\":403,\"detail\":\"En-tête X-Requested-With manquant\"}");
                return;
            }
            chain.doFilter(request, response);
        }
    }
}
