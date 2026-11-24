package ma.entraide.intranet.statistique;

import ma.entraide.intranet.common.BusinessException;
import ma.entraide.intranet.security.CurrentUser;
import ma.entraide.intranet.statistique.SaisieDtos.Ligne;
import ma.entraide.intranet.statistique.SaisieDtos.Saisie;
import ma.entraide.intranet.territoire.Province;
import ma.entraide.intranet.territoire.ScopeService;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Export Excel des statistiques d'une période. Le périmètre de l'utilisateur est toujours appliqué.
 */
@Service
public class ExportService {

    private final SaisieService saisies;
    private final ScopeService scope;

    public ExportService(SaisieService saisies, ScopeService scope) {
        this.saisies = saisies;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public byte[] xlsx(CurrentUser user, Long periodeId, Long regionId, Long provinceId) {
        List<Province> provinces = scope.visibleProvinces(user).stream()
                .filter(p -> regionId == null || regionId.equals(p.getRegionId()))
                .filter(p -> provinceId == null || provinceId.equals(p.getId()))
                .toList();
        if (provinces.isEmpty()) {
            throw BusinessException.forbidden("Aucune province de votre périmètre ne correspond à ce filtre");
        }
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Statistiques");
            Font bold = wb.createFont();
            bold.setBold(true);
            CellStyle head = wb.createCellStyle();
            head.setFont(bold);

            String[] titres = {"Province", "Centre", "Activité", "Masculins", "Féminins", "Alphabétisation",
                    "Agents titulaires", "Agents vacataires", "Agents volontaires", "Agents des associations",
                    "Agents autre département", "État du centre", "Motif / observation", "Statut de la saisie"};
            Row header = sheet.createRow(0);
            for (int i = 0; i < titres.length; i++) {
                Cell c = header.createCell(i);
                c.setCellValue(titres[i]);
                c.setCellStyle(head);
            }
            int r = 1;
            for (Province p : provinces) {
                Saisie s = saisies.get(user, periodeId, p.getId());
                for (Ligne l : s.lignes()) {
                    Row row = sheet.createRow(r++);
                    row.createCell(0).setCellValue(s.provinceNom());
                    row.createCell(1).setCellValue(l.centreNom());
                    row.createCell(2).setCellValue(l.activiteNom());
                    List<Integer> v = l.valeurs().asList();
                    for (int i = 0; i < v.size(); i++) {
                        if (v.get(i) != null) {
                            row.createCell(3 + i).setCellValue(v.get(i));
                        }
                    }
                    row.createCell(11).setCellValue(EtatCentre.valueOf(l.etat()).libelle());
                    row.createCell(12).setCellValue(l.motif() == null ? "" : l.motif());
                    row.createCell(13).setCellValue(s.statut());
                }
            }
            for (int i = 0; i < titres.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Export impossible");
        }
    }
}
