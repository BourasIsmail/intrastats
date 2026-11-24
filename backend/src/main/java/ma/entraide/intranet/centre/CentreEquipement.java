package ma.entraide.intranet.centre;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Équipements d'un centre (relation 1-1 avec centre). */
@Entity
@Table(name = "centre_equipement")
@Getter
@Setter
@NoArgsConstructor
public class CentreEquipement {

    @Id
    @Column(name = "centre_id")
    private Long centreId;

    private boolean eau;

    private boolean electricite;

    private boolean assainissement;

    private boolean telephone;

    private boolean internet;

    @Column(name = "nb_ordinateurs")
    private int nbOrdinateurs;

    @Column(name = "nb_imprimantes")
    private int nbImprimantes;
}
