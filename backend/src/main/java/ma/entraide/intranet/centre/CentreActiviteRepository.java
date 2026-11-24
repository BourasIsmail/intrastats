package ma.entraide.intranet.centre;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface CentreActiviteRepository extends JpaRepository<CentreActivite, Long> {
    List<CentreActivite> findByCentreId(Long centreId);

    List<CentreActivite> findByCentreIdIn(Collection<Long> centreIds);

    boolean existsByCentreIdAndActiviteId(Long centreId, Long activiteId);
}
