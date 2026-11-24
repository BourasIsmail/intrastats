package ma.entraide.intranet.centre;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.entraide.intranet.security.CurrentUsers;
import ma.entraide.intranet.territoire.Province;
import ma.entraide.intranet.territoire.ScopeService;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class CentreController {

    public record CentreSummary(Long id, String code, String nomFr, String nomAr, Long provinceId,
                                String provinceNom, String type, int nbActivites) {}

    public record ActiviteDto(Long id, Long activiteId, String nom, String nomAr, Integer capacite, String responsable,
                              LocalDate dateOuverture, LocalDate dateCessation, String motifCessation) {}

    public record FoncierDto(String propriete, String situationFonciere, String proprietaire,
                             BigDecimal loyerMensuel, BigDecimal chargesLocatives, String numeroContrat,
                             LocalDate dateContrat, String avenant) {}

    public record EquipementDto(boolean eau, boolean electricite, boolean assainissement, boolean telephone,
                                boolean internet, int nbOrdinateurs, int nbImprimantes) {}

    public record CentreDetail(Long id, String code, String nomFr, String nomAr, String categorie, String type,
                               Long provinceId, Long communeId, String milieu, String adresse,
                               BigDecimal latitude, BigDecimal longitude, boolean positionExacte,
                               LocalDate dateConstruction, String telephone, String responsable,
                               FoncierDto foncier, EquipementDto equipement, List<ActiviteDto> activites) {}

    public record CentreRequest(@NotBlank @Size(max = 30) String code,
                                @NotBlank @Size(max = 250) String nomFr,
                                @Size(max = 250) String nomAr,
                                String categorie, String type, Long provinceId, Long communeId, String milieu,
                                String adresse, BigDecimal latitude, BigDecimal longitude, Boolean positionExacte,
                                LocalDate dateConstruction, String telephone, String responsable,
                                @Valid FoncierDto foncier, @Valid EquipementDto equipement) {
        CentreService.CentreForm toForm() {
            return new CentreService.CentreForm(code, nomFr, nomAr, categorie, type, provinceId, communeId, milieu,
                    adresse, latitude, longitude, Boolean.TRUE.equals(positionExacte), dateConstruction, telephone,
                    responsable,
                    foncier == null ? null : new CentreService.FoncierForm(foncier.propriete(),
                            foncier.situationFonciere(), foncier.proprietaire(), foncier.loyerMensuel(),
                            foncier.chargesLocatives(), foncier.numeroContrat(), foncier.dateContrat(),
                            foncier.avenant()),
                    equipement == null ? null : new CentreService.EquipementForm(equipement.eau(),
                            equipement.electricite(), equipement.assainissement(), equipement.telephone(),
                            equipement.internet(), equipement.nbOrdinateurs(), equipement.nbImprimantes()));
        }
    }

    public record ActiviteRequest(Long activiteId, LocalDate dateOuverture, Integer capacite, String responsable) {}

    public record CessationRequest(LocalDate date, @Size(max = 255) String motif) {}

    public record CatalogueItem(Long id, String nom, String nomAr, String abreviation) {}

    public record PageDto<T>(List<T> content, long total, int page, int size) {}

    private final CentreService service;
    private final ScopeService scope;
    private final CurrentUsers current;

    public CentreController(CentreService service, ScopeService scope, CurrentUsers current) {
        this.service = service;
        this.scope = scope;
        this.current = current;
    }

    @GetMapping("/centres")
    @PreAuthorize("hasAuthority('CENTRE_LIRE')")
    public PageDto<CentreSummary> list(@RequestParam(required = false) Long provinceId,
                                       @RequestParam(required = false) String q,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "20") int size) {
        var user = current.get();
        Page<Centre> result = service.search(user, provinceId, q, page, size);
        Map<Long, Integer> counts = service.activityCounts(result.getContent());
        Map<Long, String> provinceNames = scope.visibleProvinces(user).stream()
                .collect(Collectors.toMap(Province::getId, Province::getNomFr));
        List<CentreSummary> content = result.getContent().stream()
                .map(c -> new CentreSummary(c.getId(), c.getCode(), c.getNomFr(), c.getNomAr(), c.getProvinceId(),
                        provinceNames.get(c.getProvinceId()), c.getType(), counts.getOrDefault(c.getId(), 0)))
                .toList();
        return new PageDto<>(content, result.getTotalElements(), result.getNumber(), result.getSize());
    }

    @GetMapping("/centres/{id}")
    @PreAuthorize("hasAuthority('CENTRE_LIRE')")
    public CentreDetail get(@PathVariable Long id) {
        return detail(service.get(current.get(), id));
    }

    @PostMapping("/centres")
    @PreAuthorize("hasAuthority('CENTRE_GERER')")
    public CentreDetail create(@Valid @RequestBody CentreRequest r) {
        return detail(service.create(current.get(), r.toForm()));
    }

    @PutMapping("/centres/{id}")
    @PreAuthorize("hasAuthority('CENTRE_GERER')")
    public CentreDetail update(@PathVariable Long id, @Valid @RequestBody CentreRequest r) {
        return detail(service.update(current.get(), id, r.toForm()));
    }

    @PostMapping("/centres/{id}/activites")
    @PreAuthorize("hasAuthority('CENTRE_GERER')")
    public CentreDetail openActivity(@PathVariable Long id, @RequestBody ActiviteRequest r) {
        service.openActivity(current.get(), id,
                new CentreService.ActiviteForm(r.activiteId(), r.dateOuverture(), r.capacite(), r.responsable()));
        return detail(service.get(current.get(), id));
    }

    @PostMapping("/centres/{id}/activites/{centreActiviteId}/cesser")
    @PreAuthorize("hasAuthority('CENTRE_GERER')")
    public CentreDetail closeActivity(@PathVariable Long id, @PathVariable Long centreActiviteId,
                                      @Valid @RequestBody CessationRequest r) {
        service.closeActivity(current.get(), id, centreActiviteId, r.date(), r.motif());
        return detail(service.get(current.get(), id));
    }

    @GetMapping("/activites")
    @PreAuthorize("hasAuthority('CENTRE_LIRE')")
    public List<CatalogueItem> catalog() {
        return service.catalog().stream().map(a -> new CatalogueItem(a.getId(), a.getNom(), a.getNomAr(), a.getAbreviation())).toList();
    }

    private CentreDetail detail(Centre c) {
        Map<Long, Activite> catalog = service.activityCatalog();
        List<ActiviteDto> activites = service.activitiesOf(c.getId()).stream()
                .map(a -> new ActiviteDto(a.getId(), a.getActiviteId(),
                        catalog.containsKey(a.getActiviteId()) ? catalog.get(a.getActiviteId()).getNom() : "?",
                        catalog.containsKey(a.getActiviteId()) ? catalog.get(a.getActiviteId()).getNomAr() : null,
                        a.getCapacite(), a.getResponsable(), a.getDateOuverture(), a.getDateCessation(),
                        a.getMotifCessation()))
                .toList();
        FoncierDto foncier = service.foncierOf(c.getId())
                .map(f -> new FoncierDto(f.getPropriete(), f.getSituationFonciere(), f.getProprietaire(),
                        f.getLoyerMensuel(), f.getChargesLocatives(), f.getNumeroContrat(), f.getDateContrat(),
                        f.getAvenant()))
                .orElse(null);
        EquipementDto equipement = service.equipementOf(c.getId())
                .map(e -> new EquipementDto(e.isEau(), e.isElectricite(), e.isAssainissement(), e.isTelephone(),
                        e.isInternet(), e.getNbOrdinateurs(), e.getNbImprimantes()))
                .orElse(null);
        return new CentreDetail(c.getId(), c.getCode(), c.getNomFr(), c.getNomAr(), c.getCategorie(), c.getType(),
                c.getProvinceId(), c.getCommuneId(), c.getMilieu(), c.getAdresse(), c.getLatitude(), c.getLongitude(),
                c.isPositionExacte(), c.getDateConstruction(), c.getTelephone(), c.getResponsable(),
                foncier, equipement, activites);
    }
}
