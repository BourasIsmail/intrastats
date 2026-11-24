package ma.entraide.intranet.security;

import ma.entraide.intranet.common.AuditService;
import ma.entraide.intranet.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AuthService {

    /** Hash bcrypt factice : on le compare quand l'utilisateur n'existe pas, pour garder un temps de réponse comparable. */
    private static final String DUMMY_HASH = "{bcrypt}$2a$12$C6UzMDM.H6dfI/f/IKcEeO5qaZ5zD3x5T6Qn0Qq1l2oA7mXx2fK8e";

    private final UtilisateurRepository users;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final LoginAttempts attempts;
    private final AuditService audit;

    public AuthService(UtilisateurRepository users, PasswordEncoder encoder, JwtService jwt,
                       LoginAttempts attempts, AuditService audit) {
        this.users = users;
        this.encoder = encoder;
        this.jwt = jwt;
        this.attempts = attempts;
        this.audit = audit;
    }

    public record Session(String token, UserInfo user) {}

    public record UserInfo(Long id, String nom, String login, Role role, Long regionId, Long provinceId,
                           boolean motDePasseTemporaire, List<String> droits) {}

    @Transactional
    public Session login(String login, String password) {
        if (attempts.isBlocked(login)) {
            throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS,
                    "Trop de tentatives. Réessayez dans quelques minutes.");
        }
        Utilisateur u = users.findByLogin(login).orElse(null);
        boolean ok;
        if (u == null || !u.isActif()) {
            encoder.matches(password, DUMMY_HASH);
            ok = false;
        } else {
            ok = encoder.matches(password, u.getMotDePasse());
        }
        if (!ok) {
            attempts.recordFailure(login);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "Identifiant ou mot de passe incorrect");
        }
        attempts.reset(login);

        // Migration transparente : ancien hash MySQL -> bcrypt, sans que l'utilisateur change de mot de passe.
        if (encoder.upgradeEncoding(u.getMotDePasse())) {
            u.setMotDePasse(encoder.encode(password));
            audit.log(u.getId(), "MOT_DE_PASSE_MIGRE", "utilisateur", u.getId(), "Ancien hash remplacé par bcrypt");
        }
        u.setDerniereConnexion(LocalDateTime.now());
        users.save(u);
        return new Session(jwt.issue(u), info(u));
    }

    @Transactional(readOnly = true)
    public UserInfo me(Long userId) {
        Utilisateur u = users.findById(userId)
                .filter(Utilisateur::isActif)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Compte inactif ou introuvable"));
        return info(u);
    }

    @Transactional
    public void changePassword(Long userId, String current, String next) {
        Utilisateur u = users.findById(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Compte introuvable"));
        if (!encoder.matches(current, u.getMotDePasse())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "Mot de passe actuel incorrect");
        }
        u.setMotDePasse(encoder.encode(next));
        u.setMotDePasseTemporaire(false);
        users.save(u);
        audit.log(userId, "MOT_DE_PASSE_CHANGE", "utilisateur", userId, null);
    }

    UserInfo info(Utilisateur u) {
        return new UserInfo(u.getId(), u.getNom(), u.getLogin(), u.getRole(), u.getRegionId(), u.getProvinceId(),
                u.isMotDePasseTemporaire(), jwt.droitsOf(u.getRole()));
    }
}
