package ma.entraide.intranet.territoire;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RegionRepository extends JpaRepository<Region, Long> {
    List<Region> findAllByOrderByNomFrAsc();
}
