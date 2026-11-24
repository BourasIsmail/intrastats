package ma.entraide.intranet.statistique;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StatistiqueRepository extends JpaRepository<Statistique, Long> {
    List<Statistique> findByPeriodeIdAndCentreActiviteIdIn(Long periodeId, Collection<Long> centreActiviteIds);

    Optional<Statistique> findByPeriodeIdAndCentreActiviteId(Long periodeId, Long centreActiviteId);
}
