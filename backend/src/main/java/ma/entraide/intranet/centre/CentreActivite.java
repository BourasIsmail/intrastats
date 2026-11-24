package ma.entraide.intranet.centre;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

/**
 * Activité ouverte dans un centre. dateCessation == null signifie « sans fin ».
 */
@Entity
@Table(name = "centre_activite")
@Getter
@Setter
@NoArgsConstructor
public class CentreActivite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "centre_id")
    private Long centreId;

    @Column(name = "activite_id")
    private Long activiteId;

    private String responsable;

    private Integer capacite;

    @Column(name = "date_ouverture")
    private LocalDate dateOuverture;

    @Column(name = "date_cessation")
    private LocalDate dateCessation;

    @Column(name = "motif_cessation")
    private String motifCessation;

    private String gestion;

    private String partenaire;

    @Column(name = "responsable_id")
    private Long responsableId;

    private Integer superficie;

    @Column(name = "gestion_autre")
    private String gestionAutre;

    @Column(name = "date_convention")
    private LocalDate dateConvention;

    /** Vraie si l'activité fonctionne pendant au moins un jour de la période [debut, fin]. */
    public boolean activeSur(LocalDate debut, LocalDate fin) {
        boolean ouverteAvantFin = dateOuverture == null || !dateOuverture.isAfter(fin);
        boolean nonCessee = dateCessation == null || !dateCessation.isBefore(debut);
        return ouverteAvantFin && nonCessee;
    }
}
