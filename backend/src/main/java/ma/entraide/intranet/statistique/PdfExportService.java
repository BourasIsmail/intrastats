package ma.entraide.intranet.statistique;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import ma.entraide.intranet.common.BusinessException;
import ma.entraide.intranet.security.CurrentUser;
import ma.entraide.intranet.statistique.SaisieDtos.Ligne;
import ma.entraide.intranet.statistique.SaisieDtos.Saisie;
import ma.entraide.intranet.territoire.Province;
import ma.entraide.intranet.territoire.ScopeService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rapport PDF des statistiques d'une période : une page (ou plus) par province du périmètre de l'utilisateur,
 * avec le même tableau que la saisie, les totaux et le statut de la saisie.
 */
@Service
public class PdfExportService {

    private static final Color BLEU = new Color(0x2F, 0x5F, 0x8C);
    private static final Color GRIS_CLAIR = new Color(0xF4, 0xF7, 0xFA);
    private static final Color GRIS_TEXTE = new Color(0x56, 0x65, 0x74);
    private static final Color BORDURE = new Color(0xC5, 0xCF, 0xD8);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final SaisieService saisies;
    private final ScopeService scope;

    public PdfExportService(SaisieService saisies, ScopeService scope) {
        this.saisies = saisies;
        this.scope = scope;
    }

    @Transactional(readOnly = true)
    public byte[] pdf(CurrentUser user, Long periodeId, Long regionId, Long provinceId) {
        List<Province> provinces = scope.visibleProvinces(user).stream()
                .filter(p -> regionId == null || regionId.equals(p.getRegionId()))
                .filter(p -> provinceId == null || provinceId.equals(p.getId()))
                .toList();
        if (provinces.isEmpty()) {
            throw BusinessException.forbidden("Aucune province de votre périmètre ne correspond à ce filtre");
        }
        // Les données sont lues avant d'ouvrir le document : une erreur ne produit pas de PDF à moitié écrit.
        List<Saisie> contenu = provinces.stream().map(p -> saisies.get(user, periodeId, p.getId())).toList();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4.rotate(), 28, 28, 40, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Pied(contenu.get(0).periodeLibelle(), LocalDateTime.now().format(DATE)));
            doc.addTitle("Statistiques " + contenu.get(0).periodeLibelle());
            doc.addCreator("Entraide Intranet");
            doc.open();
            boolean premiere = true;
            for (Saisie s : contenu) {
                if (!premiere) {
                    doc.newPage();
                }
                premiere = false;
                section(doc, s);
            }
        } catch (DocumentException e) {
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "Export PDF impossible");
        } finally {
            if (doc.isOpen()) {
                doc.close();
            }
        }
        return out.toByteArray();
    }

    private void section(Document doc, Saisie s) throws DocumentException {
        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        Font sous = FontFactory.getFont(FontFactory.HELVETICA, 9, GRIS_TEXTE);
        doc.add(new Paragraph("Statistiques · " + s.provinceNom(), titre));
        Paragraph p = new Paragraph(s.periodeLibelle() + " · Période " + (s.periodeOuverte() ? "ouverte" : "clôturée")
                + " · Statut de la saisie : " + libelleStatut(s.statut()), sous);
        p.setSpacingAfter(10);
        doc.add(p);

        if (s.lignes().isEmpty()) {
            doc.add(new Paragraph("Aucune activité à saisir pour cette province sur cette période.", sous));
            return;
        }

        PdfPTable t = new PdfPTable(new float[]{13, 17, 5.5f, 5.5f, 6.5f, 6, 6, 6, 6.5f, 6.5f, 8, 13.5f});
        t.setWidthPercentage(100);
        t.setHeaderRows(2);
        t.setSplitLate(false);

        en(t, "Centre", 2, 1);
        en(t, "Activité", 2, 1);
        en(t, "Nombre de bénéficiaires", 1, 3);
        en(t, "Personnel de l'Entraide Nationale", 1, 3);
        en(t, "Agents des partenaires", 1, 2);
        en(t, "État du centre", 2, 1);
        en(t, "Motif / Observation", 2, 1);
        for (String c : new String[]{"Masculins", "Féminins", "Alphabé-tisation", "Agents titulaires",
                "Agents vacataires", "Agents volontaires", "Agents associations", "Agents autre dép."}) {
            en(t, c, 1, 1);
        }

        // Le centre n'apparaît qu'une fois, sur plusieurs lignes (comme dans le tableau de saisie).
        Map<Long, Integer> parCentre = new LinkedHashMap<>();
        s.lignes().forEach(l -> parCentre.merge(l.centreId(), 1, Integer::sum));
        long[] totaux = new long[8];
        long dernierCentre = -1;
        for (Ligne l : s.lignes()) {
            if (l.centreId() != dernierCentre) {
                t.addCell(celluleCentre(l, parCentre.get(l.centreId())));
                dernierCentre = l.centreId();
            }
            t.addCell(cellule(l.activiteNom(), Element.ALIGN_LEFT, false));
            List<Integer> v = l.valeurs().asList();
            for (int i = 0; i < v.size(); i++) {
                totaux[i] += v.get(i) == null ? 0 : v.get(i);
                t.addCell(cellule(v.get(i) == null ? "" : String.valueOf(v.get(i)), Element.ALIGN_CENTER, false));
            }
            t.addCell(cellule(EtatCentre.valueOf(l.etat()).libelle(), Element.ALIGN_CENTER, false));
            t.addCell(cellule(l.motif() == null ? "" : l.motif(), Element.ALIGN_LEFT, false));
        }

        PdfPCell total = cellule("Total province", Element.ALIGN_LEFT, true);
        total.setColspan(2);
        total.setBackgroundColor(GRIS_CLAIR);
        t.addCell(total);
        for (long x : totaux) {
            PdfPCell c = cellule(String.format("%,d", x).replace(',', ' '), Element.ALIGN_CENTER, true);
            c.setBackgroundColor(GRIS_CLAIR);
            t.addCell(c);
        }
        PdfPCell vide = cellule("", Element.ALIGN_LEFT, false);
        vide.setColspan(2);
        vide.setBackgroundColor(GRIS_CLAIR);
        t.addCell(vide);
        doc.add(t);

        if (s.motifRenvoi() != null && !s.motifRenvoi().isBlank()) {
            Paragraph renvoi = new Paragraph("Motif du dernier renvoi : " + s.motifRenvoi(), sous);
            renvoi.setSpacingBefore(8);
            doc.add(renvoi);
        }
    }


    /** Cellule du centre : nom français, puis nom arabe (police embarquée, écriture de droite à gauche). */
    private PdfPCell celluleCentre(Ligne l, int lignes) throws DocumentException {
        PdfPCell centre = new PdfPCell();
        centre.setRowspan(lignes);
        centre.setBackgroundColor(GRIS_CLAIR);
        centre.setPadding(4);
        centre.setBorderColor(BORDURE);
        centre.setBorderWidth(0.5f);
        centre.addElement(new Paragraph(l.centreNom(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7.5f)));
        if (l.centreNomAr() != null && !l.centreNomAr().isBlank()) {
            PdfPCell ar = new PdfPCell(new Phrase(l.centreNomAr(), new Font(policeArabe(), 8)));
            ar.setBorder(Rectangle.NO_BORDER);
            ar.setPaddingLeft(0);
            ar.setPaddingRight(0);
            ar.setPaddingBottom(0);
            ar.setHorizontalAlignment(Element.ALIGN_RIGHT);
            ar.setRunDirection(PdfWriter.RUN_DIRECTION_RTL);
            PdfPTable inner = new PdfPTable(1);
            inner.setWidthPercentage(100);
            inner.addCell(ar);
            centre.addElement(inner);
        }
        return centre;
    }

    private static BaseFont arabe;

    private static synchronized BaseFont policeArabe() throws DocumentException {
        if (arabe == null) {
            try (var in = new ClassPathResource("fonts/DejaVuSans.ttf").getInputStream()) {
                byte[] ttf = in.readAllBytes();
                arabe = BaseFont.createFont("DejaVuSans.ttf", BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, ttf, null);
            } catch (IOException e) {
                throw new DocumentException("Police arabe introuvable : " + e.getMessage());
            }
        }
        return arabe;
    }

    private static void en(PdfPTable t, String texte, int rowspan, int colspan) {
        PdfPCell c = new PdfPCell(new Phrase(texte, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.WHITE)));
        c.setBackgroundColor(BLEU);
        c.setHorizontalAlignment(Element.ALIGN_CENTER);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setRowspan(rowspan);
        c.setColspan(colspan);
        c.setPadding(4);
        c.setBorderColor(Color.WHITE);
        t.addCell(c);
    }

    private static PdfPCell cellule(String texte, int alignement, boolean gras) {
        Font f = FontFactory.getFont(gras ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, 7.5f);
        PdfPCell c = new PdfPCell(new Phrase(texte, f));
        c.setHorizontalAlignment(alignement);
        c.setVerticalAlignment(Element.ALIGN_MIDDLE);
        c.setPadding(4);
        c.setBorderColor(BORDURE);
        c.setBorderWidth(0.5f);
        return c;
    }

    private static String libelleStatut(String statut) {
        return switch (SaisieStatut.valueOf(statut)) {
            case NON_COMMENCEE -> "Non commencé";
            case BROUILLON -> "Brouillon";
            case SOUMIS -> "Soumis";
            case VALIDE -> "Validé";
            case A_CORRIGER -> "À corriger";
        };
    }

    /** Pied de page : période, date d'édition et numéro de page. */
    private static class Pied extends PdfPageEventHelper {
        private final String periode;
        private final String edition;

        Pied(String periode, String edition) {
            this.periode = periode;
            this.edition = edition;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            Font f = FontFactory.getFont(FontFactory.HELVETICA, 7, GRIS_TEXTE);
            Rectangle page = document.getPageSize();
            float y = document.bottomMargin() - 16;
            com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_LEFT,
                    new Phrase("Entraide Intranet · Statistiques " + periode + " · édité le " + edition, f),
                    document.leftMargin(), y, 0);
            com.lowagie.text.pdf.ColumnText.showTextAligned(cb, Element.ALIGN_RIGHT,
                    new Phrase("Page " + writer.getPageNumber(), f),
                    page.getWidth() - document.rightMargin(), y, 0);
        }
    }
}
