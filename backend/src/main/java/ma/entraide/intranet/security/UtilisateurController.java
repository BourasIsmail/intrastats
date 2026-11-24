package ma.entraide.intranet.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ma.entraide.intranet.common.AuditService;
import ma.entraide.intranet.common.BusinessException;
import ma.entraide.intranet.territoire.ProvinceRepository;
import ma.entraide.intranet.territoire.RegionRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.SecureRandom;
import java.util.List;

/**
 * Gestion des comptes : réservée à l'admin (droit UTILISATEUR_GERER).
 */
@RestController
@RequestMapping("/api/utilisateurs")
@PreAuthorize("hasAuthority('UTILISATEUR_GERER')")
public class UtilisateurController {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    public record UtilisateurDto(Long id, String matricule, String nom, String login, Role role, Long regionId,
                                 Long provinceId, String email, boolean actif, boolean motDePasseTemporaire) {}

    public record Created(UtilisateurDto utilisateur, String motDePasseTemporaire) {}

    public record Form(@Size(max = 20) String matricule,
                       @NotBlank @Size(max = 120) String nom,
                       @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{3,60}", message = "3 à 60 caractères : lettres, chiffres, . _ -") String login,
                       @NotNull Role role, Long regionId, Long provinceId,
                       @Email @Size(max = 160) String email, Boolean actif) {}

    private final UtilisateurRepository users;
    private final PasswordEncoder encoder;
    private final ProvinceRepository provinces;
    private final RegionRepository regions;
    private final AuditService audit;
    private final CurrentUsers current;

    public UtilisateurController(UtilisateurRepository users, PasswordEncoder encoder, ProvinceRepository provinces,
                                 RegionRepository regions, AuditService audit, CurrentUsers current) {
        this.users = users;
        this.encoder = encoder;
        this.provinces = provinces;
        this.regions = regions;
        this.audit = audit;
        this.current = current;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<UtilisateurDto> list() {
        return users.findAll().stream().map(UtilisateurController::dto).toList();
    }

    @PostMapping
    @Transactional
    public Created create(@Valid @RequestBody Form f) {
        if (users.existsByLogin(f.login())) {
            throw BusinessException.conflict("Cet identifiant existe déjà");
        }
        Utilisateur u = new Utilisateur();
        apply(u, f);
        String temporaire = randomPassword();
        u.setMotDePasse(encoder.encode(temporaire));
        u.setMotDePasseTemporaire(true);
        u = users.save(u);
        audit.log(current.get().id(), "UTILISATEUR_CREE", "utilisateur", u.getId(), u.getLogin() + " (" + u.getRole() + ")");
        return new Created(dto(u), temporaire);
    }

    @PutMapping("/{id}")
    @Transactional
    public UtilisateurDto update(@PathVariable Long id, @Valid @RequestBody Form f) {
        Utilisateur u = users.findById(id).orElseThrow(() -> BusinessException.notFound("Utilisateur introuvable"));
        if (!u.getLogin().equals(f.login()) && users.existsByLogin(f.login())) {
            throw BusinessException.conflict("Cet identifiant existe déjà");
        }
        if (id.equals(current.get().id()) && (f.role() != u.getRole() || Boolean.FALSE.equals(f.actif()))) {
            throw BusinessException.conflict("Vous ne pouvez pas changer votre propre rôle ni vous désactiver");
        }
        apply(u, f);
        audit.log(current.get().id(), "UTILISATEUR_MODIFIE", "utilisateur", id, u.getLogin());
        return dto(users.save(u));
    }

    @PostMapping("/{id}/reinitialiser")
    @Transactional
    public Created reset(@PathVariable Long id) {
        Utilisateur u = users.findById(id).orElseThrow(() -> BusinessException.notFound("Utilisateur introuvable"));
        String temporaire = randomPassword();
        u.setMotDePasse(encoder.encode(temporaire));
        u.setMotDePasseTemporaire(true);
        audit.log(current.get().id(), "MOT_DE_PASSE_REINITIALISE", "utilisateur", id, u.getLogin());
        return new Created(dto(users.save(u)), temporaire);
    }

    private void apply(Utilisateur u, Form f) {
        switch (f.role()) {
            case PROVINCIAL -> {
                if (f.provinceId() == null || !provinces.existsById(f.provinceId())) {
                    throw BusinessException.invalid("Province obligatoire pour un compte provincial",
                            List.of("provinceId : obligatoire"));
                }
                u.setProvinceId(f.provinceId());
                u.setRegionId(null);
            }
            case REGIONAL -> {
                if (f.regionId() == null || !regions.existsById(f.regionId())) {
                    throw BusinessException.invalid("Région obligatoire pour un compte régional",
                            List.of("regionId : obligatoire"));
                }
                u.setRegionId(f.regionId());
                u.setProvinceId(null);
            }
            case ADMIN -> {
                u.setRegionId(null);
                u.setProvinceId(null);
            }
        }
        u.setMatricule(f.matricule() == null || f.matricule().isBlank() ? null : f.matricule().trim());
        u.setNom(f.nom().trim());
        u.setLogin(f.login());
        u.setRole(f.role());
        u.setEmail(f.email());
        u.setActif(f.actif() == null || f.actif());
    }

    private static UtilisateurDto dto(Utilisateur u) {
        return new UtilisateurDto(u.getId(), u.getMatricule(), u.getNom(), u.getLogin(), u.getRole(), u.getRegionId(),
                u.getProvinceId(), u.getEmail(), u.isActif(), u.isMotDePasseTemporaire());
    }

    private static String randomPassword() {
        StringBuilder sb = new StringBuilder(14);
        for (int i = 0; i < 14; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
