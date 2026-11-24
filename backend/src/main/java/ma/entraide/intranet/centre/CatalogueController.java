package ma.entraide.intranet.centre;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ma.entraide.intranet.common.AuditService;
import ma.entraide.intranet.common.BusinessException;
import ma.entraide.intranet.security.CurrentUsers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Gestion du catalogue des activités : réservée à l'admin (droit ACTIVITE_GERER).
 * La hiérarchie programme > axe > catégorie est reprise telle quelle ; on choisit seulement la catégorie.
 */
@RestController
@RequestMapping("/api/admin/activites")
public class CatalogueController {

    public record CategorieDto(Long id, String libelle) {}

    public record ActiviteCatalogueDto(Long id, String nom, String nomAr, String abreviation, String description,
                                       Long categorieId, String categorie, String typeActivite,
                                       String personnesCibles, Integer priorite, boolean actif, int nbCentres) {}

    public record ActiviteCatalogueRequest(@NotBlank @Size(max = 250) String nom, @Size(max = 250) String nomAr,
                                           @Size(max = 20) String abreviation, @Size(max = 255) String description,
                                           @NotNull Long categorieId, @Size(max = 20) String typeActivite,
                                           @Size(max = 60) String personnesCibles, Integer priorite, Boolean actif) {}

    private final ActiviteRepository activites;
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final CurrentUsers current;

    public CatalogueController(ActiviteRepository activites, JdbcTemplate jdbc, AuditService audit,
                               CurrentUsers current) {
        this.activites = activites;
        this.jdbc = jdbc;
        this.audit = audit;
        this.current = current;
    }

    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('ACTIVITE_GERER')")
    public List<CategorieDto> categories() {
        return jdbc.query("""
                SELECT c.id, CONCAT(p.nom, ' › ', a.nom, ' › ', c.nom) AS libelle
                FROM activite_categorie c JOIN axe a ON a.id = c.axe_id JOIN programme p ON p.id = a.programme_id
                ORDER BY p.nom, a.nom, c.nom
                """, (rs, i) -> new CategorieDto(rs.getLong("id"), rs.getString("libelle")));
    }

    @GetMapping
    @PreAuthorize("hasAuthority('ACTIVITE_GERER')")
    public List<ActiviteCatalogueDto> list() {
        Map<Long, String> libelles = categories().stream().collect(Collectors.toMap(CategorieDto::id, CategorieDto::libelle));
        Map<Long, Integer> usage = jdbc.query(
                "SELECT activite_id, COUNT(*) AS n FROM centre_activite WHERE date_cessation IS NULL GROUP BY activite_id",
                rs -> {
                    Map<Long, Integer> m = new java.util.HashMap<>();
                    while (rs.next()) {
                        m.put(rs.getLong(1), rs.getInt(2));
                    }
                    return m;
                });
        return activites.findAllByOrderByNomAsc().stream()
                .map(a -> dto(a, libelles, usage)).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ACTIVITE_GERER')")
    @Transactional
    public ActiviteCatalogueDto create(@Valid @RequestBody ActiviteCatalogueRequest r) {
        Activite a = new Activite();
        apply(a, r);
        a = activites.save(a);
        audit.log(current.get().id(), "ACTIVITE_CATALOGUE_CREEE", "activite", a.getId(), a.getNom());
        return dto(a, categories().stream().collect(Collectors.toMap(CategorieDto::id, CategorieDto::libelle)), Map.of());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ACTIVITE_GERER')")
    @Transactional
    public ActiviteCatalogueDto update(@PathVariable Long id, @Valid @RequestBody ActiviteCatalogueRequest r) {
        Activite a = activites.findById(id).orElseThrow(() -> BusinessException.notFound("Activité introuvable"));
        apply(a, r);
        a = activites.save(a);
        audit.log(current.get().id(), "ACTIVITE_CATALOGUE_MODIFIEE", "activite", id, a.getNom());
        return dto(a, categories().stream().collect(Collectors.toMap(CategorieDto::id, CategorieDto::libelle)), Map.of());
    }

    private void apply(Activite a, ActiviteCatalogueRequest r) {
        Long self = a.getId() == null ? -1L : a.getId();
        if (activites.existsByNomIgnoreCaseAndIdNot(r.nom().trim(), self)) {
            throw BusinessException.conflict("Une activité porte déjà ce nom");
        }
        Integer existe = jdbc.queryForObject("SELECT COUNT(*) FROM activite_categorie WHERE id = ?", Integer.class, r.categorieId());
        if (existe == null || existe == 0) {
            throw BusinessException.invalid("Catégorie inconnue", List.of("categorieId : inconnue"));
        }
        String type = r.typeActivite() == null || r.typeActivite().isBlank() ? "PRINCIPALE" : r.typeActivite().trim().toUpperCase();
        if (!type.equals("PRINCIPALE") && !type.equals("SECONDAIRE")) {
            throw BusinessException.invalid("Type d'activité invalide", List.of("typeActivite : PRINCIPALE ou SECONDAIRE"));
        }
        if (r.priorite() != null && (r.priorite() < 0 || r.priorite() > 999)) {
            throw BusinessException.invalid("Priorité invalide", List.of("priorite : entre 0 et 999"));
        }
        a.setNom(r.nom().trim());
        a.setNomAr(blankToNull(r.nomAr()));
        a.setAbreviation(blankToNull(r.abreviation()));
        a.setDescription(blankToNull(r.description()));
        a.setCategorieId(r.categorieId());
        a.setTypeActivite(type);
        a.setPersonnesCibles(blankToNull(r.personnesCibles()));
        a.setPriorite(r.priorite());
        a.setActif(r.actif() == null || r.actif());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static ActiviteCatalogueDto dto(Activite a, Map<Long, String> libelles, Map<Long, Integer> usage) {
        return new ActiviteCatalogueDto(a.getId(), a.getNom(), a.getNomAr(), a.getAbreviation(), a.getDescription(),
                a.getCategorieId(), libelles.get(a.getCategorieId()), a.getTypeActivite(), a.getPersonnesCibles(),
                a.getPriorite(), a.isActif(), usage.getOrDefault(a.getId(), 0));
    }
}
