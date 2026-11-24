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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "centre")
@Getter
@Setter
@NoArgsConstructor
public class Centre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String code;

    @Column(name = "nom_fr")
    private String nomFr;

    @Column(name = "nom_ar")
    private String nomAr;

    private String categorie;

    private String type;

    @Column(name = "province_id")
    private Long provinceId;

    @Column(name = "commune_id")
    private Long communeId;

    private String milieu;

    private String adresse;

    private BigDecimal latitude;

    private BigDecimal longitude;

    @Column(name = "position_exacte")
    private boolean positionExacte;

    @Column(name = "date_construction")
    private LocalDate dateConstruction;

    private String telephone;

    private String responsable;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
