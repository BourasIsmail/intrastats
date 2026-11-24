package ma.entraide.intranet.centre;

import ma.entraide.intranet.centre.ActiviteRepository;
import ma.entraide.intranet.centre.CentreActiviteRepository;
import ma.entraide.intranet.centre.CentreRepository;
import ma.entraide.intranet.common.AuditService;
import ma.entraide.intranet.common.BusinessException;
import ma.entraide.intranet.security.CurrentUser;
import ma.entraide.intranet.territoire.Province;
import ma.entraide.intranet.territoire.ScopeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CentreService {

    public record FoncierForm(String propriete, String situationFonciere, String proprietaire,
                              BigDecimal loyerMensuel, BigDecimal chargesLocatives, String numeroContrat,
                              LocalDate dateContrat, String avenant) {}

    public record EquipementForm(boolean eau, boolean electricite, boolean assainissement, boolean telephone,
                                 boolean internet, int nbOrdinateurs, int nbImprimantes) {}

    public record CentreForm(String code, String nomFr, String nomAr, String categorie, String type,
                             Long provinceId, Long communeId, String milieu, String adresse,
                             BigDecimal latitude, BigDecimal longitude, boolean positionExacte,
                             LocalDate dateConstruction, String telephone, String responsable,
                             FoncierForm foncier, EquipementForm equipement) {}

    public record ActiviteForm(Long activiteId, LocalDate dateOuverture, Integer capacite, String responsable) {}

    private final CentreRepository centres;
    private final ActiviteRepository activites;
    private final CentreActiviteRepository centreActivites;
    private final CentreFoncierRepository fonciers;
    private final CentreEquipementRepository equipements;
    private final ScopeService scope;
    private final AuditService audit;

    public CentreService(CentreRepository centres, ActiviteRepository activites,
                         CentreActiviteRepository centreActivites, CentreFoncierRepository fonciers,
                         CentreEquipementRepository equipements, ScopeService scope, AuditService audit) {
        this.centres = centres;
        this.activites = activites;
        this.centreActivites = centreActivites;
        this.fonciers = fonciers;
        this.equipements = equipements;
        this.scope = scope;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Optional<CentreFoncier> foncierOf(Long centreId) {
        return fonciers.findById(centreId);
    }

    @Transactional(readOnly = true)
    public Optional<CentreEquipement> equipementOf(Long centreId) {
        return equipements.findById(centreId);
    }

    private void saveExtras(Long centreId, CentreForm f) {
        if (f.foncier() != null) {
            FoncierForm x = f.foncier();
            CentreFoncier c = fonciers.findById(centreId).orElseGet(() -> {
                CentreFoncier n = new CentreFoncier();
                n.setCentreId(centreId);
                return n;
            });
            c.setPropriete(x.propriete());
            c.setSituationFonciere(x.situationFonciere());
            c.setProprietaire(x.proprietaire());
            c.setLoyerMensuel(x.loyerMensuel());
            c.setChargesLocatives(x.chargesLocatives());
            c.setNumeroContrat(x.numeroContrat());
            c.setDateContrat(x.dateContrat());
            c.setAvenant(x.avenant());
            fonciers.save(c);
        }
        if (f.equipement() != null) {
            EquipementForm x = f.equipement();
            if (x.nbOrdinateurs() < 0 || x.nbImprimantes() < 0) {
                throw BusinessException.invalid("Équipements invalides", List.of("nombres : positifs ou nuls"));
            }
            CentreEquipement c = equipements.findById(centreId).orElseGet(() -> {
                CentreEquipement n = new CentreEquipement();
                n.setCentreId(centreId);
                return n;
            });
            c.setEau(x.eau());
            c.setElectricite(x.electricite());
            c.setAssainissement(x.assainissement());
            c.setTelephone(x.telephone());
            c.setInternet(x.internet());
            c.setNbOrdinateurs(x.nbOrdinateurs());
            c.setNbImprimantes(x.nbImprimantes());
            equipements.save(c);
        }
    }

    @Transactional(readOnly = true)
    public Page<Centre> search(CurrentUser user, Long provinceId, String q, int page, int size) {
        List<Long> visible = scope.visibleProvinces(user).stream().map(Province::getId).toList();
        if (visible.isEmpty()) {
            return Page.empty();
        }
        if (provinceId != null && !visible.contains(provinceId)) {
            throw BusinessException.forbidden("Cette province est hors de votre périmètre");
        }
        String like = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        return centres.search(visible, provinceId, like, PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
    }

    @Transactional(readOnly = true)
    public Centre get(CurrentUser user, Long id) {
        Centre c = centres.findById(id).orElseThrow(() -> BusinessException.notFound("Centre introuvable"));
        scope.assertCanRead(user, c.getProvinceId());
        return c;
    }

    @Transactional(readOnly = true)
    public Map<Long, Integer> activityCounts(List<Centre> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<CentreActivite>> byCentre = centreActivites
                .findByCentreIdIn(list.stream().map(Centre::getId).toList()).stream()
                .collect(Collectors.groupingBy(CentreActivite::getCentreId));
        return list.stream().collect(Collectors.toMap(Centre::getId,
                c -> (int) byCentre.getOrDefault(c.getId(), List.of()).stream()
                        .filter(a -> a.getDateCessation() == null).count()));
    }

    @Transactional(readOnly = true)
    public List<CentreActivite> activitiesOf(Long centreId) {
        return centreActivites.findByCentreId(centreId);
    }

    @Transactional(readOnly = true)
    public Map<Long, Activite> activityCatalog() {
        return activites.findAll().stream().collect(Collectors.toMap(Activite::getId, Function.identity()));
    }

    @Transactional(readOnly = true)
    public List<Activite> catalog() {
        return activites.findAllByOrderByNomAsc();
    }

    @Transactional
    public Centre create(CurrentUser user, CentreForm f) {
        Long provinceId = user.isAdmin() ? f.provinceId() : user.provinceId();
        if (provinceId == null) {
            throw BusinessException.invalid("Province obligatoire", List.of("provinceId : obligatoire"));
        }
        scope.assertCanWrite(user, provinceId);
        if (centres.existsByCode(f.code())) {
            throw BusinessException.conflict("Ce code de centre existe déjà");
        }
        Centre c = new Centre();
        apply(c, f, provinceId);
        c.setCreatedBy(user.id());
        c.setUpdatedBy(user.id());
        c = centres.save(c);
        saveExtras(c.getId(), f);
        audit.log(user.id(), "CENTRE_CREE", "centre", c.getId(), c.getCode());
        return c;
    }

    @Transactional
    public Centre update(CurrentUser user, Long id, CentreForm f) {
        Centre c = centres.findById(id).orElseThrow(() -> BusinessException.notFound("Centre introuvable"));
        scope.assertCanWrite(user, c.getProvinceId());
        // Un provincial ne peut pas déplacer un centre vers une autre province.
        Long provinceId = user.isAdmin() && f.provinceId() != null ? f.provinceId() : c.getProvinceId();
        if (!c.getCode().equals(f.code()) && centres.existsByCode(f.code())) {
            throw BusinessException.conflict("Ce code de centre existe déjà");
        }
        apply(c, f, provinceId);
        c.setUpdatedBy(user.id());
        c.setUpdatedAt(Instant.now());
        audit.log(user.id(), "CENTRE_MODIFIE", "centre", id, c.getCode());
        c = centres.save(c);
        saveExtras(id, f);
        return c;
    }

    @Transactional
    public CentreActivite openActivity(CurrentUser user, Long centreId, ActiviteForm f) {
        Centre c = centres.findById(centreId).orElseThrow(() -> BusinessException.notFound("Centre introuvable"));
        scope.assertCanWrite(user, c.getProvinceId());
        if (f.activiteId() == null || !activites.existsById(f.activiteId())) {
            throw BusinessException.invalid("Activité inconnue", List.of("activiteId : inconnue"));
        }
        if (centreActivites.existsByCentreIdAndActiviteId(centreId, f.activiteId())) {
            throw BusinessException.conflict("Cette activité existe déjà dans ce centre");
        }
        CentreActivite ca = new CentreActivite();
        ca.setCentreId(centreId);
        ca.setActiviteId(f.activiteId());
        ca.setDateOuverture(f.dateOuverture() == null ? LocalDate.now() : f.dateOuverture());
        ca.setCapacite(f.capacite());
        ca.setResponsable(f.responsable());
        ca = centreActivites.save(ca);
        audit.log(user.id(), "ACTIVITE_OUVERTE", "centre_activite", ca.getId(), "centre " + centreId);
        return ca;
    }

    @Transactional
    public CentreActivite closeActivity(CurrentUser user, Long centreId, Long centreActiviteId,
                                        LocalDate date, String motif) {
        Centre c = centres.findById(centreId).orElseThrow(() -> BusinessException.notFound("Centre introuvable"));
        scope.assertCanWrite(user, c.getProvinceId());
        CentreActivite ca = centreActivites.findById(centreActiviteId)
                .filter(a -> a.getCentreId().equals(centreId))
                .orElseThrow(() -> BusinessException.notFound("Activité du centre introuvable"));
        ca.setDateCessation(date == null ? LocalDate.now() : date);
        ca.setMotifCessation(motif);
        audit.log(user.id(), "ACTIVITE_CESSEE", "centre_activite", centreActiviteId, motif);
        return centreActivites.save(ca);
    }

    private void apply(Centre c, CentreForm f, Long provinceId) {
        c.setCode(f.code());
        c.setNomFr(f.nomFr());
        c.setNomAr(f.nomAr());
        c.setCategorie(f.categorie());
        c.setType(f.type());
        c.setProvinceId(provinceId);
        c.setCommuneId(f.communeId());
        c.setMilieu(f.milieu());
        c.setAdresse(f.adresse());
        c.setLatitude(f.latitude());
        c.setLongitude(f.longitude());
        c.setPositionExacte(f.positionExacte());
        c.setDateConstruction(f.dateConstruction());
        c.setTelephone(f.telephone());
        c.setResponsable(f.responsable());
    }
}
