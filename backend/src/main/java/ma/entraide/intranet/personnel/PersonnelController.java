package ma.entraide.intranet.personnel;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.entraide.intranet.centre.CentreController.PageDto;
import ma.entraide.intranet.centre.Centre;
import ma.entraide.intranet.centre.CentreRepository;
import ma.entraide.intranet.security.CurrentUsers;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/personnel")
public class PersonnelController {

    public record PersonnelDto(Long id, String matricule, String nom, String prenom, String cin,
                               LocalDate dateNaissance, String lieuNaissance, String sexe,
                               LocalDate dateRecrutement, String fonction, String autreFonction, Long provinceId,
                               Long centreId, String centreNom, String adresse, String telephone,
                               String situationFamiliale, int nbEnfants, String niveauScolaire, boolean actif) {}

    public record PersonnelRequest(@Size(max = 20) String matricule, @NotBlank @Size(max = 60) String nom,
                                   @NotBlank @Size(max = 60) String prenom, @Size(max = 20) String cin,
                                   LocalDate dateNaissance, @Size(max = 100) String lieuNaissance,
                                   @Size(max = 1) String sexe, LocalDate dateRecrutement,
                                   @Size(max = 60) String fonction, @Size(max = 60) String autreFonction,
                                   Long provinceId, Long centreId, @Size(max = 400) String adresse,
                                   @Size(max = 50) String telephone, @Size(max = 20) String situationFamiliale,
                                   Integer nbEnfants, @Size(max = 40) String niveauScolaire, Boolean actif) {
        PersonnelService.PersonnelForm toForm() {
            return new PersonnelService.PersonnelForm(matricule, nom, prenom, cin, dateNaissance, lieuNaissance, sexe,
                    dateRecrutement, fonction, autreFonction, provinceId, centreId, adresse, telephone,
                    situationFamiliale, nbEnfants, niveauScolaire, actif);
        }
    }

    private final PersonnelService service;
    private final CentreRepository centres;
    private final CurrentUsers current;

    public PersonnelController(PersonnelService service, CentreRepository centres, CurrentUsers current) {
        this.service = service;
        this.centres = centres;
        this.current = current;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PERSONNEL_LIRE')")
    public PageDto<PersonnelDto> list(@RequestParam(required = false) Long provinceId,
                                      @RequestParam(required = false) Long centreId,
                                      @RequestParam(required = false) String q,
                                      @RequestParam(defaultValue = "false") boolean actifSeulement,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        Page<Personnel> r = service.search(current.get(), provinceId, centreId, q, actifSeulement, page, size);
        Map<Long, String> noms = centreNames(r.getContent());
        return new PageDto<>(r.getContent().stream().map(p -> dto(p, noms)).toList(), r.getTotalElements(),
                r.getNumber(), r.getSize());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PERSONNEL_LIRE')")
    public PersonnelDto get(@PathVariable Long id) {
        Personnel p = service.get(current.get(), id);
        return dto(p, centreNames(List.of(p)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERSONNEL_GERER')")
    public PersonnelDto create(@Valid @RequestBody PersonnelRequest r) {
        Personnel p = service.create(current.get(), r.toForm());
        return dto(p, centreNames(List.of(p)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERSONNEL_GERER')")
    public PersonnelDto update(@PathVariable Long id, @Valid @RequestBody PersonnelRequest r) {
        Personnel p = service.update(current.get(), id, r.toForm());
        return dto(p, centreNames(List.of(p)));
    }

    private Map<Long, String> centreNames(List<Personnel> list) {
        List<Long> ids = list.stream().map(Personnel::getCentreId).filter(java.util.Objects::nonNull).distinct().toList();
        return centres.findAllById(ids).stream().collect(Collectors.toMap(Centre::getId, Centre::getNomFr));
    }

    private static PersonnelDto dto(Personnel p, Map<Long, String> centreNoms) {
        return new PersonnelDto(p.getId(), p.getMatricule(), p.getNom(), p.getPrenom(), p.getCin(),
                p.getDateNaissance(), p.getLieuNaissance(), p.getSexe(), p.getDateRecrutement(), p.getFonction(),
                p.getAutreFonction(), p.getProvinceId(), p.getCentreId(),
                p.getCentreId() == null ? null : centreNoms.get(p.getCentreId()), p.getAdresse(), p.getTelephone(),
                p.getSituationFamiliale(), p.getNbEnfants(), p.getNiveauScolaire(), p.isActif());
    }
}
