package ma.entraide.intranet.territoire;

import ma.entraide.intranet.common.BusinessException;
import ma.entraide.intranet.security.CurrentUser;
import ma.entraide.intranet.security.Role;
import ma.entraide.intranet.territoire.ProvinceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Périmètre géographique d'un utilisateur :
 * ADMIN = tout, REGIONAL = les provinces de sa région, PROVINCIAL = sa province.
 */
@Service
public class ScopeService {

    private final ProvinceRepository provinces;

    public ScopeService(ProvinceRepository provinces) {
        this.provinces = provinces;
    }

    public List<Province> visibleProvinces(CurrentUser user) {
        return switch (user.role()) {
            case ADMIN -> provinces.findAllByOrderByNomFrAsc();
            case REGIONAL -> user.regionId() == null ? List.of()
                    : provinces.findByRegionIdOrderByNomFrAsc(user.regionId());
            case PROVINCIAL -> user.provinceId() == null ? List.<Province>of()
                    : provinces.findById(user.provinceId()).map(List::of).orElse(List.of());
        };
    }

    public boolean canRead(CurrentUser user, Long provinceId) {
        if (provinceId == null) {
            return false;
        }
        return switch (user.role()) {
            case ADMIN -> true;
            case PROVINCIAL -> provinceId.equals(user.provinceId());
            case REGIONAL -> user.regionId() != null && provinces.findById(provinceId)
                    .map(p -> user.regionId().equals(p.getRegionId())).orElse(false);
        };
    }

    public void assertCanRead(CurrentUser user, Long provinceId) {
        if (!canRead(user, provinceId)) {
            throw BusinessException.forbidden("Cette province est hors de votre périmètre");
        }
    }

    /** Écriture sur les centres et la saisie : admin partout, provincial dans sa province, jamais le régional. */
    public void assertCanWrite(CurrentUser user, Long provinceId) {
        boolean ok = user.role() == Role.ADMIN
                || (user.role() == Role.PROVINCIAL && provinceId != null && provinceId.equals(user.provinceId()));
        if (!ok) {
            throw BusinessException.forbidden("Vous ne pouvez pas modifier les données de cette province");
        }
    }

    public Optional<Province> province(Long id) {
        return provinces.findById(id);
    }
}
