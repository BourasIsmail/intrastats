package ma.entraide.intranet.territoire;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface CommuneRepository extends JpaRepository<Commune, Long> {
    List<Commune> findByProvinceIdOrderByNomFrAsc(Long provinceId);
}
