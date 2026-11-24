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

@Entity
@Table(name = "activite")
@Getter
@Setter
@NoArgsConstructor
public class Activite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String nom;

    @Column(name = "nom_ar")
    private String nomAr;

    private String abreviation;

    private String description;

    @Column(name = "categorie_id")
    private Long categorieId;

    @Column(name = "type_activite")
    private String typeActivite = "PRINCIPALE";

    @Column(name = "personnes_cibles")
    private String personnesCibles;

    private Integer priorite;

    private boolean actif = true;
}
