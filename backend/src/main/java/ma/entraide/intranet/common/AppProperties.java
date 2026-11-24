package ma.entraide.intranet.common;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration applicative (préfixe "app").
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(Jwt jwt, Cookie cookie) {

    public record Jwt(String secret, Duration ttl) {
        public Jwt {
            if (secret == null || secret.getBytes().length < 32) {
                throw new IllegalStateException("app.jwt.secret doit faire au moins 32 octets");
            }
            if (ttl == null) {
                ttl = Duration.ofHours(8);
            }
        }
    }

    public record Cookie(String name, boolean secure) {
        public Cookie {
            if (name == null || name.isBlank()) {
                name = "access_token";
            }
        }
    }
}
