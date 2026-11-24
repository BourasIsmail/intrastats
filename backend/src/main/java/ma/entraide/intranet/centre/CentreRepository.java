package ma.entraide.intranet.centre;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface CentreRepository extends JpaRepository<Centre, Long> {

    @Query("""
            select c from Centre c
            where c.provinceId in :provinceIds
              and (:provinceId is null or c.provinceId = :provinceId)
              and (lower(c.nomFr) like :q or lower(c.code) like :q or lower(coalesce(c.nomAr, '')) like :q)
            order by c.nomFr
            """)
    Page<Centre> search(@Param("provinceIds") Collection<Long> provinceIds,
                        @Param("provinceId") Long provinceId,
                        @Param("q") String q,
                        Pageable pageable);

    List<Centre> findByProvinceIdOrderByNomFrAsc(Long provinceId);

    boolean existsByCode(String code);
}
