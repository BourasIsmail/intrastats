package ma.entraide.intranet.periode;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import ma.entraide.intranet.security.CurrentUsers;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/periodes")
public class PeriodeController {

    public record PeriodeDto(Long id, int mois, int annee, String anneeStat, String statut, boolean compteeEnStats) {
        static PeriodeDto of(Periode p) {
            return new PeriodeDto(p.getId(), p.getMois(), p.getAnnee(), p.getAnneeStat(), p.getStatut().name(),
                    p.isCompteeEnStats());
        }
    }

    public record CreateRequest(@Min(1) @Max(12) int mois, @Min(2000) @Max(2100) int annee,
                                String anneeStat, Boolean compteeEnStats) {}

    private final PeriodeService service;
    private final CurrentUsers current;

    public PeriodeController(PeriodeService service, CurrentUsers current) {
        this.service = service;
        this.current = current;
    }

    @GetMapping
    public List<PeriodeDto> list() {
        return service.list().stream().map(PeriodeDto::of).toList();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PERIODE_GERER')")
    public PeriodeDto create(@Valid @RequestBody CreateRequest r) {
        return PeriodeDto.of(service.create(r.mois(), r.annee(), r.anneeStat(),
                r.compteeEnStats() == null || r.compteeEnStats(), current.get()));
    }

    @PostMapping("/{id}/cloturer")
    @PreAuthorize("hasAuthority('PERIODE_GERER')")
    public PeriodeDto close(@PathVariable Long id) {
        return PeriodeDto.of(service.close(id, current.get()));
    }

    @PostMapping("/{id}/rouvrir")
    @PreAuthorize("hasAuthority('PERIODE_GERER')")
    public PeriodeDto reopen(@PathVariable Long id) {
        return PeriodeDto.of(service.reopen(id, current.get()));
    }
}
