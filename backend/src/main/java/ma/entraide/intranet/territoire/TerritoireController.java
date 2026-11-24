package ma.entraide.intranet.territoire;

import ma.entraide.intranet.common.BusinessException;
import ma.entraide.intranet.security.CurrentUser;
import ma.entraide.intranet.security.CurrentUsers;
import ma.entraide.intranet.territoire.CommuneRepository;
import ma.entraide.intranet.territoire.RegionRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class TerritoireController {

    public record RegionDto(Long id, String nomFr, String nomAr) {}

    public record ProvinceDto(Long id, String nomFr, String nomAr, Long regionId) {}

    public record CommuneDto(Long id, String nomFr, String nomAr, Long provinceId) {}

    private final RegionRepository regions;
    private final CommuneRepository communes;
    private final ScopeService scope;
    private final CurrentUsers current;

    public TerritoireController(RegionRepository regions, CommuneRepository communes,
                                ScopeService scope, CurrentUsers current) {
        this.regions = regions;
        this.communes = communes;
        this.scope = scope;
        this.current = current;
    }

    /** Régions visibles : celles des provinces du périmètre de l'utilisateur. */
    @GetMapping("/regions")
    public List<RegionDto> regions() {
        CurrentUser user = current.get();
        Set<Long> ids = scope.visibleProvinces(user).stream().map(Province::getRegionId).collect(Collectors.toSet());
        return regions.findAllByOrderByNomFrAsc().stream()
                .filter(r -> ids.contains(r.getId()))
                .map(r -> new RegionDto(r.getId(), r.getNomFr(), r.getNomAr()))
                .toList();
    }

    @GetMapping("/provinces")
    public List<ProvinceDto> provinces(@RequestParam(required = false) Long regionId) {
        return scope.visibleProvinces(current.get()).stream()
                .filter(p -> regionId == null || regionId.equals(p.getRegionId()))
                .map(p -> new ProvinceDto(p.getId(), p.getNomFr(), p.getNomAr(), p.getRegionId()))
                .toList();
    }

    @GetMapping("/communes")
    public List<CommuneDto> communes(@RequestParam Long provinceId) {
        CurrentUser user = current.get();
        if (!scope.canRead(user, provinceId)) {
            throw BusinessException.forbidden("Cette province est hors de votre périmètre");
        }
        return communes.findByProvinceIdOrderByNomFrAsc(provinceId).stream()
                .map(c -> new CommuneDto(c.getId(), c.getNomFr(), c.getNomAr(), c.getProvinceId()))
                .toList();
    }
}
