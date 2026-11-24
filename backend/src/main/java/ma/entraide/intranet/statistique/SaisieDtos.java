package ma.entraide.intranet.statistique;

import java.time.Instant;
import java.util.List;

/**
 * Objets échangés avec l'interface pour la saisie, la vérification et la consultation.
 */
public final class SaisieDtos {

    private SaisieDtos() {}

    /** Une ligne du tableau : une activité dans un centre. */
    public record Ligne(Long centreActiviteId, Long centreId, String centreNom, String centreNomAr, String activiteNom, String activiteNomAr,
                        Valeurs valeurs, Valeurs precedentes, List<String> ecarts, String etat, String motif) {}

    public record Saisie(Long periodeId, String periodeLibelle, boolean periodeOuverte,
                         Long provinceId, String provinceNom, String provinceNomAr, Long regionId, String statut,
                         String motifRenvoi,
                         boolean modifiable, Instant soumiseLe, List<Ligne> lignes) {}

    /** Ligne envoyée par le provincial pour enregistrement. */
    public record LigneInput(Long centreActiviteId, Valeurs valeurs, String etat, String motif) {}

    public record Resume(Long provinceId, String provinceNom, String provinceNomAr, Long regionId, String regionNom,
                         String regionNomAr, String statut,
                         Instant soumiseLe, int nbLignes) {}

    public record TableauDeBord(Long periodeId, String periodeLibelle, boolean periodeOuverte,
                                int provinces, int nonCommencees, int brouillons, int soumises, int validees,
                                int aCorriger, int centres, int activites, long beneficiaires) {}
}
