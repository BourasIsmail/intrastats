package ma.entraide.intranet.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Journal d'audit : qui a fait quoi (clôture/réouverture de période, décisions de vérification, etc.).
 */
@Service
public class AuditService {

    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void log(Long utilisateurId, String action, String entite, Object entiteId, String details) {
        jdbc.update(
                "INSERT INTO journal_audit (utilisateur_id, action, entite, entite_id, details) VALUES (?, ?, ?, ?, ?)",
                utilisateurId, action, entite, entiteId == null ? null : String.valueOf(entiteId),
                details != null && details.length() > 1000 ? details.substring(0, 1000) : details);
    }
}
