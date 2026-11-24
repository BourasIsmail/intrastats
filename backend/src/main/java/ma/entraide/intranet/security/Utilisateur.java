package ma.entraide.intranet.security;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "utilisateur")
@Getter
@Setter
@NoArgsConstructor
public class Utilisateur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String matricule;

    private String nom;

    private String login;

    @Column(name = "mot_de_passe")
    private String motDePasse;

    @Enumerated(EnumType.STRING)
    private Role role;

    @Column(name = "region_id")
    private Long regionId;

    @Column(name = "province_id")
    private Long provinceId;

    private String email;

    private boolean actif = true;

    @Column(name = "mot_de_passe_temporaire")
    private boolean motDePasseTemporaire;

    @Column(name = "derniere_connexion")
    private LocalDateTime derniereConnexion;
}
