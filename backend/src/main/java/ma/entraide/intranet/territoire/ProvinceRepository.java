package ma.entraide.intranet.territoire;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProvinceRepository extends JpaRepository<Province, Long> {
    List<Province> findAllByOrderByNomFrAsc();

    List<Province> findByRegionIdOrderByNomFrAsc(Long regionId);
}
