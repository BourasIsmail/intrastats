package ma.entraide.intranet.personnel;

import ma.entraide.intranet.centre.Centre;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Personnel : visible et modifiable par l'admin (tout) et le provincial (sa province). Le régional n'y a pas accès
 * (données personnelles : CIN, adresse, situation familiale).
 */
@Service
public class PersonnelService {

    public record PersonnelForm(String matricule, String nom, String prenom, String cin, LocalDate dateNaissance,
                                String lieuNaissance, String sexe, LocalDate dateRecrutement, String fonction,
                                String autreFonction, Long provinceId, Long centreId, String adresse,
                                String telephone, String situationFamiliale, Integer nbEnfants,
                                String niveauScolaire, Boolean actif) {}

    private final PersonnelRepository personnel;
    private final CentreRepository centres;
    private final ScopeService scope;
    private final AuditService audit;

    public PersonnelService(PersonnelRepository personnel, CentreRepository centres, ScopeService scope,
                            AuditService audit) {
        this.personnel = personnel;
        this.centres = centres;
        this.scope = scope;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Page<Personnel> search(CurrentUser user, Long provinceId, Long centreId, String q, boolean actifSeulement,
                                  int page, int size) {
        List<Long> visible = scope.visibleProvinces(user).stream().map(Province::getId).toList();
        if (visible.isEmpty()) {
            return Page.empty();
        }
        if (provinceId != null && !visible.contains(provinceId)) {
            throw BusinessException.forbidden("Cette province est hors de votre périmètre");
        }
        String like = "%" + (q == null ? "" : q.trim().toLowerCase()) + "%";
        return personnel.search(visible, provinceId, centreId, actifSeulement, like,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 200)));
    }

    @Transactional(readOnly = true)
    public Personnel get(CurrentUser user, Long id) {
        Personnel p = personnel.findById(id).orElseThrow(() -> BusinessException.notFound("Agent introuvable"));
        scope.assertCanRead(user, p.getProvinceId());
        return p;
    }

    @Transactional
    public Personnel create(CurrentUser user, PersonnelForm f) {
        Personnel p = new Personnel();
        apply(user, p, f, true);
        p = personnel.save(p);
        audit.log(user.id(), "PERSONNEL_CREE", "personnel", p.getId(), p.getNom() + " " + p.getPrenom());
        return p;
    }

    @Transactional
    public Personnel update(CurrentUser user, Long id, PersonnelForm f) {
        Personnel p = personnel.findById(id).orElseThrow(() -> BusinessException.notFound("Agent introuvable"));
        scope.assertCanWrite(user, p.getProvinceId());
        apply(user, p, f, false);
        audit.log(user.id(), "PERSONNEL_MODIFIE", "personnel", id, p.getNom() + " " + p.getPrenom());
        return personnel.save(p);
    }

    private void apply(CurrentUser user, Personnel p, PersonnelForm f, boolean creation) {
        List<String> erreurs = new ArrayList<>();
        if (blank(f.nom())) {
            erreurs.add("nom : obligatoire");
        }
        if (blank(f.prenom())) {
            erreurs.add("prénom : obligatoire");
        }
        if (f.sexe() != null && !f.sexe().isBlank() && !f.sexe().equals("M") && !f.sexe().equals("F")) {
            erreurs.add("sexe : M ou F");
        }
        if (f.nbEnfants() != null && (f.nbEnfants() < 0 || f.nbEnfants() > 30)) {
            erreurs.add("nombre d'enfants : entre 0 et 30");
        }
        if (f.dateNaissance() != null && f.dateRecrutement() != null && f.dateRecrutement().isBefore(f.dateNaissance())) {
            erreurs.add("date de recrutement : antérieure à la naissance");
        }
        Centre centre = null;
        if (f.centreId() != null) {
            centre = centres.findById(f.centreId()).orElse(null);
            if (centre == null) {
                erreurs.add("centre : inconnu");
            }
        }
        if (!erreurs.isEmpty()) {
            throw BusinessException.invalid("Agent invalide", erreurs);
        }

        // La province vient du centre s'il y en a un ; sinon de l'utilisateur (provincial) ou de la demande (admin).
        Long provinceId = centre != null ? centre.getProvinceId()
                : (user.isAdmin() ? f.provinceId() : user.provinceId());
        if (provinceId == null) {
            throw BusinessException.invalid("Province obligatoire", List.of("provinceId : obligatoire"));
        }
        scope.assertCanWrite(user, provinceId);
        if (!creation && !provinceId.equals(p.getProvinceId()) && !user.isAdmin()) {
            throw BusinessException.forbidden("Vous ne pouvez pas déplacer un agent vers une autre province");
        }

        String cin = clean(f.cin());
        String matricule = clean(f.matricule());
        Long self = p.getId() == null ? -1L : p.getId();
        if (cin != null && personnel.existsByCinIgnoreCaseAndIdNot(cin, self)) {
            throw BusinessException.conflict("Ce CIN existe déjà");
        }
        if (matricule != null && personnel.existsByMatriculeAndIdNot(matricule, self)) {
            throw BusinessException.conflict("Ce matricule existe déjà");
        }

        p.setMatricule(matricule);
        p.setNom(f.nom().trim());
        p.setPrenom(f.prenom().trim());
        p.setCin(cin);
        p.setDateNaissance(f.dateNaissance());
        p.setLieuNaissance(clean(f.lieuNaissance()));
        p.setSexe(clean(f.sexe()));
        p.setDateRecrutement(f.dateRecrutement());
        p.setFonction(clean(f.fonction()));
        p.setAutreFonction(clean(f.autreFonction()));
        p.setProvinceId(provinceId);
        p.setCentreId(f.centreId());
        p.setAdresse(clean(f.adresse()));
        p.setTelephone(clean(f.telephone()));
        p.setSituationFamiliale(clean(f.situationFamiliale()));
        p.setNbEnfants(f.nbEnfants() == null ? 0 : f.nbEnfants());
        p.setNiveauScolaire(clean(f.niveauScolaire()));
        p.setActif(f.actif() == null || f.actif());
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }

    private static String clean(String s) {
        return blank(s) ? null : s.trim();
    }
}
