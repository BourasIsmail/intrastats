package ma.entraide.intranet.centre;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Foncier et contrat d'un centre (relation 1-1 avec centre). */
@Entity
@Table(name = "centre_foncier")
@Getter
@Setter
@NoArgsConstructor
public class CentreFoncier {

    @Id
    @Column(name = "centre_id")
    private Long centreId;

    private String propriete;

    @Column(name = "situation_fonciere")
    private String situationFonciere;

    private String proprietaire;

    @Column(name = "loyer_mensuel")
    private BigDecimal loyerMensuel;

    @Column(name = "charges_locatives")
    private BigDecimal chargesLocatives;

    @Column(name = "numero_contrat")
    private String numeroContrat;

    @Column(name = "date_contrat")
    private LocalDate dateContrat;

    private String avenant;
}
