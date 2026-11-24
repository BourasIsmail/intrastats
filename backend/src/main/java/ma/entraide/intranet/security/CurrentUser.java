package ma.entraide.intranet.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

/**
 * Utilisateur courant, reconstruit à partir des claims du jeton.
 */
public record CurrentUser(Long id, String login, Role role, Long regionId, Long provinceId, List<String> droits) {

    public static CurrentUser from(Jwt jwt) {
        return new CurrentUser(
                Long.valueOf(jwt.getSubject()),
                jwt.getClaimAsString("login"),
                Role.valueOf(jwt.getClaimAsString("role")),
                asLong(jwt.getClaim("regionId")),
                asLong(jwt.getClaim("provinceId")),
                jwt.getClaimAsStringList("droits") == null ? List.of() : jwt.getClaimAsStringList("droits"));
    }

    private static Long asLong(Object value) {
        return value == null ? null : ((Number) value).longValue();
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    public boolean hasDroit(String droit) {
        return droits.contains(droit);
    }
}
