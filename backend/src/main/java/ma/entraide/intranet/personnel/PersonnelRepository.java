package ma.entraide.intranet.personnel;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PersonnelRepository extends JpaRepository<Personnel, Long> {

    @Query("""
            select p from Personnel p
            where p.provinceId in :provinceIds
              and (:provinceId is null or p.provinceId = :provinceId)
              and (:centreId is null or p.centreId = :centreId)
              and (:actifSeulement = false or p.actif = true)
              and (lower(p.nom) like :q or lower(p.prenom) like :q
                   or lower(coalesce(p.matricule, '')) like :q or lower(coalesce(p.cin, '')) like :q)
            order by p.nom, p.prenom
            """)
    Page<Personnel> search(@Param("provinceIds") Collection<Long> provinceIds,
                           @Param("provinceId") Long provinceId,
                           @Param("centreId") Long centreId,
                           @Param("actifSeulement") boolean actifSeulement,
                           @Param("q") String q,
                           Pageable pageable);

    List<Personnel> findByIdIn(Collection<Long> ids);

    boolean existsByCinIgnoreCase(String cin);

    boolean existsByCinIgnoreCaseAndIdNot(String cin, Long id);

    boolean existsByMatricule(String matricule);

    boolean existsByMatriculeAndIdNot(String matricule, Long id);
}
