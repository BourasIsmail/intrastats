package ma.entraide.intranet.statistique;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SaisieProvinceRepository extends JpaRepository<SaisieProvince, Long> {
    Optional<SaisieProvince> findByProvinceIdAndPeriodeId(Long provinceId, Long periodeId);

    List<SaisieProvince> findByPeriodeId(Long periodeId);
}
