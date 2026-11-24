package ma.entraide.intranet.security;

import ma.entraide.intranet.common.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class JwtService {

    private final JwtEncoder encoder;
    private final AppProperties props;
    private final JdbcTemplate jdbc;

    public JwtService(JwtEncoder encoder, AppProperties props, JdbcTemplate jdbc) {
        this.encoder = encoder;
        this.props = props;
        this.jdbc = jdbc;
    }

    /** Droits du rôle, lus dans role_droit : on peut en ajouter sans modifier le code. */
    public List<String> droitsOf(Role role) {
        return jdbc.queryForList("SELECT droit_code FROM role_droit WHERE role = ? ORDER BY droit_code",
                String.class, role.name());
    }

    public String issue(Utilisateur u) {
        Instant now = Instant.now();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject(String.valueOf(u.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(props.jwt().ttl()))
                .claim("login", u.getLogin())
                .claim("role", u.getRole().name())
                .claim("droits", droitsOf(u.getRole()));
        if (u.getRegionId() != null) {
            claims.claim("regionId", u.getRegionId());
        }
        if (u.getProvinceId() != null) {
            claims.claim("provinceId", u.getProvinceId());
        }
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
