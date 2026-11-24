package ma.entraide.intranet.territoire;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Table(name = "commune")
@Getter
@Setter
@NoArgsConstructor
public class Commune {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nom_fr")
    private String nomFr;

    @Column(name = "nom_ar")
    private String nomAr;

    @Column(name = "province_id")
    private Long provinceId;

    private boolean indh;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(name = "code_arcgis")
    private Integer codeArcgis;
}
