package ma.entraide.intranet.statistique;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import ma.entraide.intranet.security.CurrentUsers;
import ma.entraide.intranet.statistique.SaisieDtos.LigneInput;
import ma.entraide.intranet.statistique.SaisieDtos.Resume;
import ma.entraide.intranet.statistique.SaisieDtos.Saisie;
import ma.entraide.intranet.statistique.SaisieDtos.TableauDeBord;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api")
public class StatistiqueController {

    public record SaveRequest(List<LigneInput> lignes) {}

    public record RenvoiRequest(@NotBlank @Size(max = 500) String motif) {}

    private final SaisieService service;
    private final ExportService export;
    private final CurrentUsers current;

    private final PdfExportService pdf;

    public StatistiqueController(SaisieService service, ExportService export, PdfExportService pdf,
                                 CurrentUsers current) {
        this.service = service;
        this.export = export;
        this.pdf = pdf;
        this.current = current;
    }

    /** Tableau de saisie ou de consultation d'une province pour une période (périmètre contrôlé). */
    @GetMapping("/statistiques")
    @PreAuthorize("hasAuthority('STATISTIQUE_LIRE')")
    public Saisie get(@RequestParam Long periodeId, @RequestParam Long provinceId) {
        return service.get(current.get(), periodeId, provinceId);
    }

    @PutMapping("/statistiques/lignes")
    @PreAuthorize("hasAuthority('STATISTIQUE_SAISIR')")
    public Saisie save(@RequestParam Long periodeId, @RequestParam Long provinceId, @RequestBody SaveRequest body) {
        return service.save(current.get(), periodeId, provinceId, body.lignes() == null ? List.of() : body.lignes());
    }

    @PostMapping("/statistiques/soumettre")
    @PreAuthorize("hasAuthority('STATISTIQUE_SAISIR')")
    public Saisie submit(@RequestParam Long periodeId, @RequestParam Long provinceId) {
        return service.submit(current.get(), periodeId, provinceId);
    }

    @PostMapping("/statistiques/brouillon")
    @PreAuthorize("hasAuthority('STATISTIQUE_SAISIR')")
    public Saisie backToDraft(@RequestParam Long periodeId, @RequestParam Long provinceId) {
        return service.backToDraft(current.get(), periodeId, provinceId);
    }

    @PostMapping("/statistiques/valider")
    @PreAuthorize("hasAuthority('STATISTIQUE_VERIFIER')")
    public Saisie validate(@RequestParam Long periodeId, @RequestParam Long provinceId) {
        return service.validate(current.get(), periodeId, provinceId);
    }

    @PostMapping("/statistiques/renvoyer")
    @PreAuthorize("hasAuthority('STATISTIQUE_VERIFIER')")
    public Saisie returnForCorrection(@RequestParam Long periodeId, @RequestParam Long provinceId,
                                      @RequestBody RenvoiRequest body) {
        return service.returnForCorrection(current.get(), periodeId, provinceId, body.motif());
    }

    /** File de vérification / avancement : une ligne par province du périmètre. */
    @GetMapping("/saisies")
    @PreAuthorize("hasAuthority('STATISTIQUE_LIRE')")
    public List<Resume> resumes(@RequestParam Long periodeId,
                                @RequestParam(required = false) Long regionId,
                                @RequestParam(required = false) Long provinceId,
                                @RequestParam(required = false) SaisieStatut statut) {
        return service.resumes(current.get(), periodeId, regionId, provinceId, statut);
    }

    @GetMapping("/tableau-de-bord")
    @PreAuthorize("hasAuthority('STATISTIQUE_LIRE')")
    public TableauDeBord dashboard(@RequestParam Long periodeId) {
        return service.tableauDeBord(current.get(), periodeId);
    }

    @GetMapping("/statistiques/export.pdf")
    @PreAuthorize("hasAuthority('STATISTIQUE_LIRE')")
    public ResponseEntity<byte[]> exportPdf(@RequestParam Long periodeId,
                                            @RequestParam(required = false) Long regionId,
                                            @RequestParam(required = false) Long provinceId) {
        byte[] data = pdf.pdf(current.get(), periodeId, regionId, provinceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("statistiques-periode-" + periodeId + ".pdf", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(data);
    }

    @GetMapping("/statistiques/export.xlsx")
    @PreAuthorize("hasAuthority('STATISTIQUE_LIRE')")
    public ResponseEntity<byte[]> exportXlsx(@RequestParam Long periodeId,
                                             @RequestParam(required = false) Long regionId,
                                             @RequestParam(required = false) Long provinceId) {
        byte[] data = export.xlsx(current.get(), periodeId, regionId, provinceId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("statistiques-periode-" + periodeId + ".xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(data);
    }
}
