package ma.entraide.intranet.periode;

import ma.entraide.intranet.common.AuditService;
import ma.entraide.intranet.common.BusinessException;
import ma.entraide.intranet.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Cycle de vie d'une période : OUVERTE (saisie possible) puis CLOTUREE (tout verrouillé).
 * Une seule période peut être ouverte à la fois. Seul l'admin crée, clôture ou rouvre.
 */
@Service
public class PeriodeService {

    private final PeriodeRepository repo;
    private final AuditService audit;

    public PeriodeService(PeriodeRepository repo, AuditService audit) {
        this.repo = repo;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<Periode> list() {
        return repo.findAllByOrderByAnneeDescMoisDesc();
    }

    @Transactional(readOnly = true)
    public Periode get(Long id) {
        return repo.findById(id).orElseThrow(() -> BusinessException.notFound("Période introuvable"));
    }

    @Transactional
    public Periode create(int mois, int annee, String anneeStat, boolean compteeEnStats, CurrentUser by) {
        if (mois < 1 || mois > 12) {
            throw BusinessException.invalid("Mois invalide", List.of("mois : entre 1 et 12"));
        }
        if (repo.existsByAnneeAndMois(annee, mois)) {
            throw BusinessException.conflict("Cette période existe déjà");
        }
        if (repo.findByStatut(Periode.Statut.OUVERTE).isPresent()) {
            throw BusinessException.conflict("Une période est déjà ouverte : clôturez-la avant d'en ouvrir une autre");
        }
        Periode p = new Periode();
        p.setMois(mois);
        p.setAnnee(annee);
        p.setAnneeStat(anneeStat == null || anneeStat.isBlank() ? String.valueOf(annee) : anneeStat);
        p.setCompteeEnStats(compteeEnStats);
        p.setStatut(Periode.Statut.OUVERTE);
        p = repo.save(p);
        audit.log(by.id(), "PERIODE_CREEE", "periode", p.getId(), mois + "/" + annee);
        return p;
    }

    @Transactional
    public Periode close(Long id, CurrentUser by) {
        Periode p = get(id);
        if (!p.isOuverte()) {
            throw BusinessException.conflict("La période est déjà clôturée");
        }
        p.setStatut(Periode.Statut.CLOTUREE);
        audit.log(by.id(), "PERIODE_CLOTUREE", "periode", id, null);
        return repo.save(p);
    }

    @Transactional
    public Periode reopen(Long id, CurrentUser by) {
        Periode p = get(id);
        if (p.isOuverte()) {
            throw BusinessException.conflict("La période est déjà ouverte");
        }
        if (repo.findByStatut(Periode.Statut.OUVERTE).isPresent()) {
            throw BusinessException.conflict("Une autre période est ouverte : clôturez-la d'abord");
        }
        p.setStatut(Periode.Statut.OUVERTE);
        audit.log(by.id(), "PERIODE_ROUVERTE", "periode", id, null);
        return repo.save(p);
    }
}
