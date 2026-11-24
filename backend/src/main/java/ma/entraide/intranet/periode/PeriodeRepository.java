package ma.entraide.intranet.periode;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PeriodeRepository extends JpaRepository<Periode, Long> {

    List<Periode> findAllByOrderByAnneeDescMoisDesc();

    Optional<Periode> findByStatut(Periode.Statut statut);

    boolean existsByAnneeAndMois(int annee, int mois);

    Optional<Periode> findByAnneeAndMois(int annee, int mois);
}
