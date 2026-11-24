package ma.entraide.intranet.statistique;

import ma.entraide.intranet.centre.Activite;
import ma.entraide.intranet.centre.Centre;
import ma.entraide.intranet.centre.CentreActivite;
import ma.entraide.intranet.centre.ActiviteRepository;
import ma.entraide.intranet.centre.CentreActiviteRepository;
import ma.entraide.intranet.centre.CentreRepository;
import ma.entraide.intranet.common.AuditService;
import ma.entraide.intranet.common.BusinessException;
import ma.entraide.intranet.periode.Periode;
import ma.entraide.intranet.periode.PeriodeRepository;
import ma.entraide.intranet.security.CurrentUser;
import ma.entraide.intranet.security.Role;
import ma.entraide.intranet.statistique.SaisieDtos.Ligne;
import ma.entraide.intranet.statistique.SaisieDtos.LigneInput;
import ma.entraide.intranet.statistique.SaisieDtos.Resume;
import ma.entraide.intranet.statistique.SaisieDtos.Saisie;
import ma.entraide.intranet.statistique.SaisieDtos.TableauDeBord;
import ma.entraide.intranet.statistique.SaisieProvinceRepository;
import ma.entraide.intranet.statistique.StatistiqueRepository;
import ma.entraide.intranet.territoire.Province;
import ma.entraide.intranet.territoire.Region;
import ma.entraide.intranet.territoire.ScopeService;
import ma.entraide.intranet.territoire.RegionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Saisie mensuelle des statistiques par province : brouillon, soumission, vérification par l'admin.
 */
@Service
public class SaisieService {

    private static final String[] MOIS = {"Janvier", "Février", "Mars", "Avril", "Mai", "Juin", "Juillet", "Août",
            "Septembre", "Octobre", "Novembre", "Décembre"};

    private final PeriodeRepository periodes;
    private final CentreRepository centres;
    private final CentreActiviteRepository centreActivites;
    private final ActiviteRepository activites;
    private final StatistiqueRepository stats;
    private final SaisieProvinceRepository saisies;
    private final RegionRepository regions;
    private final ScopeService scope;
    private final AuditService audit;

    public SaisieService(PeriodeRepository periodes, CentreRepository centres, CentreActiviteRepository centreActivites,
                         ActiviteRepository activites, StatistiqueRepository stats, SaisieProvinceRepository saisies,
                         RegionRepository regions, ScopeService scope, AuditService audit) {
        this.periodes = periodes;
        this.centres = centres;
        this.centreActivites = centreActivites;
        this.activites = activites;
        this.stats = stats;
        this.saisies = saisies;
        this.regions = regions;
        this.scope = scope;
        this.audit = audit;
    }

    public static String libelle(Periode p) {
        return MOIS[p.getMois() - 1] + " " + p.getAnnee();
    }

    // ------------------------------------------------------------------ lecture

    @Transactional(readOnly = true)
    public Saisie get(CurrentUser user, Long periodeId, Long provinceId) {
        scope.assertCanRead(user, provinceId);
        Periode periode = periode(periodeId);
        Province province = scope.province(provinceId)
                .orElseThrow(() -> BusinessException.notFound("Province introuvable"));
        Optional<SaisieProvince> saisie = saisies.findByProvinceIdAndPeriodeId(provinceId, periodeId);
        SaisieStatut statut = saisie.map(SaisieProvince::getStatut).orElse(SaisieStatut.NON_COMMENCEE);

        boolean modifiable = user.role() == Role.PROVINCIAL && provinceId.equals(user.provinceId())
                && periode.isOuverte() && statut.modifiable();

        return new Saisie(periode.getId(), libelle(periode), periode.isOuverte(), provinceId, province.getNomFr(),
                province.getNomAr(), province.getRegionId(), statut.name(), saisie.map(SaisieProvince::getMotifRenvoi).orElse(null),
                modifiable, saisie.map(SaisieProvince::getSoumiseLe).orElse(null), lignes(periode, provinceId));
    }

    /** Une ligne par activité ouverte dans un centre de la province pendant la période. */
    private List<Ligne> lignes(Periode periode, Long provinceId) {
        List<Centre> centresProvince = centres.findByProvinceIdOrderByNomFrAsc(provinceId);
        if (centresProvince.isEmpty()) {
            return List.of();
        }
        Map<Long, Centre> centreById = centresProvince.stream().collect(Collectors.toMap(Centre::getId, Function.identity()));
        Map<Long, Activite> catalogue = activites.findAll().stream().collect(Collectors.toMap(Activite::getId, Function.identity()));
        LocalDate debut = periode.debut();
        LocalDate fin = periode.fin();
        List<CentreActivite> toutes = centreActivites.findByCentreIdIn(centreById.keySet());
        if (toutes.isEmpty()) {
            return List.of();
        }
        // Une activité figure dans le tableau si elle fonctionnait pendant la période, ou si des chiffres existent
        // déjà pour cette période (historique : activité cessée depuis, ou dates d'ouverture reprises approximativement).
        Map<Long, Statistique> courantes = stats.findByPeriodeIdAndCentreActiviteIdIn(periode.getId(),
                        toutes.stream().map(CentreActivite::getId).toList()).stream()
                .collect(Collectors.toMap(Statistique::getCentreActiviteId, Function.identity()));
        List<CentreActivite> actives = toutes.stream()
                .filter(a -> a.activeSur(debut, fin) || courantes.containsKey(a.getId()))
                .toList();
        if (actives.isEmpty()) {
            return List.of();
        }
        List<Long> ids = actives.stream().map(CentreActivite::getId).toList();
        Map<Long, Statistique> precedentes = periodePrecedente(periode)
                .map(p -> stats.findByPeriodeIdAndCentreActiviteIdIn(p.getId(), ids).stream()
                        .collect(Collectors.toMap(Statistique::getCentreActiviteId, Function.identity())))
                .orElse(Map.of());

        List<Ligne> lignes = new ArrayList<>();
        for (CentreActivite ca : actives) {
            Centre c = centreById.get(ca.getCentreId());
            Activite a = catalogue.get(ca.getActiviteId());
            Statistique s = courantes.get(ca.getId());
            Valeurs actuelles = Valeurs.of(s);
            Valeurs avant = Valeurs.of(precedentes.get(ca.getId()));
            lignes.add(new Ligne(ca.getId(), c.getId(), c.getNomFr(), c.getNomAr(), a == null ? "?" : a.getNom(),
                    a == null ? null : a.getNomAr(), actuelles, avant,
                    ecarts(actuelles, avant), s == null ? EtatCentre.ACTIF.name() : s.getEtatCentre().name(),
                    s == null ? null : s.getMotif()));
        }
        lignes.sort(Comparator.comparing(Ligne::centreNom).thenComparing(Ligne::activiteNom));
        return lignes;
    }

    private List<String> ecarts(Valeurs actuelles, Valeurs avant) {
        List<String> ecarts = new ArrayList<>();
        List<Integer> a = actuelles.asList();
        List<Integer> b = avant.asList();
        for (int i = 0; i < a.size(); i++) {
            if (Regles.ecartNotable(a.get(i), b.get(i))) {
                ecarts.add(Valeurs.CLES.get(i));
            }
        }
        return ecarts;
    }

    private Optional<Periode> periodePrecedente(Periode p) {
        LocalDate prev = p.debut().minusMonths(1);
        return periodes.findByAnneeAndMois(prev.getYear(), prev.getMonthValue());
    }

    // ------------------------------------------------------------------ saisie (provincial)

    @Transactional
    public Saisie save(CurrentUser user, Long periodeId, Long provinceId, List<LigneInput> inputs) {
        Periode periode = assertCanEnter(user, periodeId, provinceId);
        SaisieProvince saisie = saisies.findByProvinceIdAndPeriodeId(provinceId, periodeId).orElseGet(() -> {
            SaisieProvince s = new SaisieProvince();
            s.setProvinceId(provinceId);
            s.setPeriodeId(periodeId);
            s.setStatut(SaisieStatut.BROUILLON);
            return saisies.save(s);
        });
        if (!saisie.getStatut().modifiable()) {
            throw BusinessException.conflict("La saisie est " + saisie.getStatut() + " : elle n'est plus modifiable");
        }
        Map<Long, CentreActivite> permises = activitesDeLaProvince(periode, provinceId);
        List<String> erreurs = new ArrayList<>();
        for (LigneInput in : inputs) {
            if (!permises.containsKey(in.centreActiviteId())) {
                throw BusinessException.invalid("Activité hors de la province ou fermée pour cette période",
                        List.of("centreActiviteId " + in.centreActiviteId() + " : non autorisé"));
            }
            Valeurs v = in.valeurs() == null ? Valeurs.vide() : in.valeurs();
            erreurs.addAll(Regles.bornes(v).stream().map(e -> "Activité " + in.centreActiviteId() + " : " + e).toList());
        }
        if (!erreurs.isEmpty()) {
            throw BusinessException.invalid("Valeurs invalides", erreurs);
        }
        for (LigneInput in : inputs) {
            Statistique s = stats.findByPeriodeIdAndCentreActiviteId(periodeId, in.centreActiviteId())
                    .orElseGet(() -> {
                        Statistique n = new Statistique();
                        n.setCentreActiviteId(in.centreActiviteId());
                        n.setPeriodeId(periodeId);
                        return n;
                    });
            (in.valeurs() == null ? Valeurs.vide() : in.valeurs()).applyTo(s);
            s.setEtatCentre(etat(in.etat()));
            s.setMotif(in.motif() == null || in.motif().isBlank() ? null : in.motif().trim());
            s.setUpdatedAt(Instant.now());
            s.setUpdatedBy(user.id());
            stats.save(s);
        }
        return get(user, periodeId, provinceId);
    }

    @Transactional
    public Saisie submit(CurrentUser user, Long periodeId, Long provinceId) {
        Periode periode = assertCanEnter(user, periodeId, provinceId);
        SaisieProvince saisie = saisies.findByProvinceIdAndPeriodeId(provinceId, periodeId)
                .orElseThrow(() -> BusinessException.conflict("Rien n'a encore été saisi pour cette période"));
        if (!saisie.getStatut().modifiable()) {
            throw BusinessException.conflict("La saisie est déjà " + saisie.getStatut());
        }
        List<Ligne> lignes = lignes(periode, provinceId);
        if (lignes.isEmpty()) {
            throw BusinessException.conflict("Aucune activité à saisir dans cette province pour cette période");
        }
        List<String> erreurs = new ArrayList<>();
        for (Ligne l : lignes) {
            for (String e : Regles.complet(l.valeurs(), EtatCentre.valueOf(l.etat()), l.motif())) {
                erreurs.add(l.centreNom() + " · " + l.activiteNom() + " : " + e);
            }
        }
        if (!erreurs.isEmpty()) {
            throw BusinessException.invalid("La saisie contient des erreurs", erreurs);
        }
        saisie.setStatut(SaisieStatut.SOUMIS);
        saisie.setSoumiseLe(Instant.now());
        saisie.setSoumisePar(user.id());
        saisie.setMotifRenvoi(null);
        saisies.save(saisie);
        audit.log(user.id(), "SAISIE_SOUMISE", "saisie_province", saisie.getId(),
                "province " + provinceId + ", période " + periodeId);
        return get(user, periodeId, provinceId);
    }

    /** Le provincial reprend sa saisie tant que la période est ouverte et qu'elle n'est pas validée. */
    @Transactional
    public Saisie backToDraft(CurrentUser user, Long periodeId, Long provinceId) {
        assertCanEnter(user, periodeId, provinceId);
        SaisieProvince saisie = saisies.findByProvinceIdAndPeriodeId(provinceId, periodeId)
                .orElseThrow(() -> BusinessException.conflict("Aucune saisie à reprendre"));
        if (saisie.getStatut() != SaisieStatut.SOUMIS) {
            throw BusinessException.conflict("Seule une saisie soumise peut repasser en brouillon");
        }
        saisie.setStatut(SaisieStatut.BROUILLON);
        saisies.save(saisie);
        audit.log(user.id(), "SAISIE_REPRISE", "saisie_province", saisie.getId(), null);
        return get(user, periodeId, provinceId);
    }

    // ------------------------------------------------------------------ vérification (admin)

    @Transactional
    public Saisie validate(CurrentUser user, Long periodeId, Long provinceId) {
        SaisieProvince saisie = soumise(periodeId, provinceId);
        saisie.setStatut(SaisieStatut.VALIDE);
        saisie.setDecisionLe(Instant.now());
        saisie.setDecisionPar(user.id());
        saisie.setMotifRenvoi(null);
        saisies.save(saisie);
        audit.log(user.id(), "SAISIE_VALIDEE", "saisie_province", saisie.getId(),
                "province " + provinceId + ", période " + periodeId);
        return get(user, periodeId, provinceId);
    }

    @Transactional
    public Saisie returnForCorrection(CurrentUser user, Long periodeId, Long provinceId, String motif) {
        if (motif == null || motif.trim().length() < Regles.MOTIF_MIN) {
            throw BusinessException.invalid("Motif obligatoire pour un renvoi",
                    List.of("motif : " + Regles.MOTIF_MIN + " caractères minimum"));
        }
        Periode periode = periode(periodeId);
        if (!periode.isOuverte()) {
            throw BusinessException.conflict("La période est clôturée : rouvrez-la avant de renvoyer une saisie");
        }
        SaisieProvince saisie = soumise(periodeId, provinceId);
        saisie.setStatut(SaisieStatut.A_CORRIGER);
        saisie.setMotifRenvoi(motif.trim());
        saisie.setDecisionLe(Instant.now());
        saisie.setDecisionPar(user.id());
        saisies.save(saisie);
        audit.log(user.id(), "SAISIE_RENVOYEE", "saisie_province", saisie.getId(), motif.trim());
        return get(user, periodeId, provinceId);
    }

    private SaisieProvince soumise(Long periodeId, Long provinceId) {
        periode(periodeId);
        SaisieProvince saisie = saisies.findByProvinceIdAndPeriodeId(provinceId, periodeId)
                .orElseThrow(() -> BusinessException.conflict("Cette province n'a rien soumis pour cette période"));
        if (saisie.getStatut() != SaisieStatut.SOUMIS) {
            throw BusinessException.conflict("Seule une saisie soumise peut être vérifiée (statut actuel : "
                    + saisie.getStatut() + ")");
        }
        return saisie;
    }

    // ------------------------------------------------------------------ listes et tableau de bord

    @Transactional(readOnly = true)
    public List<Resume> resumes(CurrentUser user, Long periodeId, Long regionId, Long provinceId, SaisieStatut filtre) {
        Periode periode = periode(periodeId);
        Map<Long, SaisieProvince> parProvince = saisies.findByPeriodeId(periodeId).stream()
                .collect(Collectors.toMap(SaisieProvince::getProvinceId, Function.identity()));
        Map<Long, Region> nomsRegions = regions.findAll().stream()
                .collect(Collectors.toMap(Region::getId, Function.identity()));
        List<Resume> resumes = new ArrayList<>();
        for (Province p : scope.visibleProvinces(user)) {
            if (regionId != null && !regionId.equals(p.getRegionId())) {
                continue;
            }
            if (provinceId != null && !provinceId.equals(p.getId())) {
                continue;
            }
            SaisieProvince s = parProvince.get(p.getId());
            SaisieStatut statut = s == null ? SaisieStatut.NON_COMMENCEE : s.getStatut();
            if (filtre != null && filtre != statut) {
                continue;
            }
            Region region = nomsRegions.get(p.getRegionId());
            resumes.add(new Resume(p.getId(), p.getNomFr(), p.getNomAr(), p.getRegionId(),
                    region == null ? null : region.getNomFr(), region == null ? null : region.getNomAr(),
                    statut.name(), s == null ? null : s.getSoumiseLe(), lignes(periode, p.getId()).size()));
        }
        return resumes;
    }

    @Transactional(readOnly = true)
    public TableauDeBord tableauDeBord(CurrentUser user, Long periodeId) {
        Periode periode = periode(periodeId);
        List<Province> visibles = scope.visibleProvinces(user);
        Map<Long, SaisieProvince> parProvince = saisies.findByPeriodeId(periodeId).stream()
                .collect(Collectors.toMap(SaisieProvince::getProvinceId, Function.identity()));
        Map<SaisieStatut, Integer> compte = new HashMap<>();
        int centresCount = 0;
        int activitesCount = 0;
        long beneficiaires = 0;
        for (Province p : visibles) {
            SaisieProvince s = parProvince.get(p.getId());
            compte.merge(s == null ? SaisieStatut.NON_COMMENCEE : s.getStatut(), 1, Integer::sum);
            centresCount += centres.findByProvinceIdOrderByNomFrAsc(p.getId()).size();
            for (Ligne l : lignes(periode, p.getId())) {
                activitesCount++;
                beneficiaires += (l.valeurs().masculins() == null ? 0 : l.valeurs().masculins())
                        + (l.valeurs().feminins() == null ? 0 : l.valeurs().feminins());
            }
        }
        return new TableauDeBord(periode.getId(), libelle(periode), periode.isOuverte(), visibles.size(),
                compte.getOrDefault(SaisieStatut.NON_COMMENCEE, 0), compte.getOrDefault(SaisieStatut.BROUILLON, 0),
                compte.getOrDefault(SaisieStatut.SOUMIS, 0), compte.getOrDefault(SaisieStatut.VALIDE, 0),
                compte.getOrDefault(SaisieStatut.A_CORRIGER, 0), centresCount, activitesCount, beneficiaires);
    }

    // ------------------------------------------------------------------ outils

    private Periode periode(Long id) {
        return periodes.findById(id).orElseThrow(() -> BusinessException.notFound("Période introuvable"));
    }

    /** Seul le provincial de la province saisit, et seulement sur la période ouverte. */
    private Periode assertCanEnter(CurrentUser user, Long periodeId, Long provinceId) {
        if (user.role() != Role.PROVINCIAL || !provinceId.equals(user.provinceId())) {
            throw BusinessException.forbidden("Seul le compte provincial de cette province peut saisir");
        }
        Periode p = periode(periodeId);
        if (!p.isOuverte()) {
            throw BusinessException.conflict("La période est clôturée : la saisie est verrouillée");
        }
        return p;
    }

    private Map<Long, CentreActivite> activitesDeLaProvince(Periode periode, Long provinceId) {
        List<Long> centreIds = centres.findByProvinceIdOrderByNomFrAsc(provinceId).stream().map(Centre::getId).toList();
        if (centreIds.isEmpty()) {
            return Map.of();
        }
        List<CentreActivite> toutes = centreActivites.findByCentreIdIn(centreIds);
        if (toutes.isEmpty()) {
            return Map.of();
        }
        // Même règle que pour l'affichage du tableau (voir lignes()).
        java.util.Set<Long> avecChiffres = stats.findByPeriodeIdAndCentreActiviteIdIn(periode.getId(),
                        toutes.stream().map(CentreActivite::getId).toList()).stream()
                .map(Statistique::getCentreActiviteId).collect(Collectors.toSet());
        return toutes.stream()
                .filter(a -> a.activeSur(periode.debut(), periode.fin()) || avecChiffres.contains(a.getId()))
                .collect(Collectors.toMap(CentreActivite::getId, Function.identity()));
    }

    private EtatCentre etat(String value) {
        if (value == null || value.isBlank()) {
            return EtatCentre.ACTIF;
        }
        try {
            return EtatCentre.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw BusinessException.invalid("État du centre inconnu", List.of("etat : " + value));
        }
    }
}
