package ma.entraide.intranet.statistique;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "statistique")
@Getter
@Setter
@NoArgsConstructor
public class Statistique {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "centre_activite_id")
    private Long centreActiviteId;

    @Column(name = "periode_id")
    private Long periodeId;

    private Integer masculins;

    private Integer feminins;

    private Integer alphabetisation;

    @Column(name = "agents_titulaires")
    private Integer agentsTitulaires;

    @Column(name = "agents_vacataires")
    private Integer agentsVacataires;

    @Column(name = "agents_volontaires")
    private Integer agentsVolontaires;

    @Column(name = "agents_associations")
    private Integer agentsAssociations;

    @Column(name = "agents_autre_dep")
    private Integer agentsAutreDep;

    @Enumerated(EnumType.STRING)
    @Column(name = "etat_centre")
    private EtatCentre etatCentre = EtatCentre.ACTIF;

    private String motif;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private Long updatedBy;
}
