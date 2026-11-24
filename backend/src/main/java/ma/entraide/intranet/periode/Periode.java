package ma.entraide.intranet.periode;

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

import java.time.LocalDate;

@Entity
@Table(name = "periode")
@Getter
@Setter
@NoArgsConstructor
public class Periode {

    public enum Statut { OUVERTE, CLOTUREE }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int mois;

    private int annee;

    @Column(name = "annee_stat")
    private String anneeStat;

    @Enumerated(EnumType.STRING)
    private Statut statut = Statut.OUVERTE;

    @Column(name = "comptee_en_stats")
    private boolean compteeEnStats = true;

    public boolean isOuverte() {
        return statut == Statut.OUVERTE;
    }

    public LocalDate debut() {
        return LocalDate.of(annee, mois, 1);
    }

    public LocalDate fin() {
        return debut().plusMonths(1).minusDays(1);
    }
}
