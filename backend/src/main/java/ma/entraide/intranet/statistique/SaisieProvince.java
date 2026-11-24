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
@Table(name = "saisie_province")
@Getter
@Setter
@NoArgsConstructor
public class SaisieProvince {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "province_id")
    private Long provinceId;

    @Column(name = "periode_id")
    private Long periodeId;

    @Enumerated(EnumType.STRING)
    private SaisieStatut statut = SaisieStatut.BROUILLON;

    @Column(name = "motif_renvoi")
    private String motifRenvoi;

    @Column(name = "soumise_le")
    private Instant soumiseLe;

    @Column(name = "soumise_par")
    private Long soumisePar;

    @Column(name = "decision_le")
    private Instant decisionLe;

    @Column(name = "decision_par")
    private Long decisionPar;
}
