package ma.entraide.intranet.personnel;

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

@Entity
@Table(name = "personnel")
@Getter
@Setter
@NoArgsConstructor
public class Personnel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String matricule;
    private String nom;
    private String prenom;
    private String cin;

    @Column(name = "date_naissance")
    private LocalDate dateNaissance;

    @Column(name = "lieu_naissance")
    private String lieuNaissance;

    private String sexe;

    @Column(name = "date_recrutement")
    private LocalDate dateRecrutement;

    private String fonction;

    @Column(name = "autre_fonction")
    private String autreFonction;

    @Column(name = "province_id")
    private Long provinceId;

    @Column(name = "centre_id")
    private Long centreId;

    private String adresse;
    private String telephone;

    @Column(name = "situation_familiale")
    private String situationFamiliale;

    @Column(name = "nb_enfants")
    private int nbEnfants;

    @Column(name = "niveau_scolaire")
    private String niveauScolaire;

    private boolean actif = true;
}
