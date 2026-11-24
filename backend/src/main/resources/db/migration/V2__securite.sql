-- Utilisateurs, rôles (ADMIN, PROVINCIAL, REGIONAL) et droits.
-- Les droits sont rattachés aux rôles : on peut en ajouter plus tard sans changer le code des rôles.

CREATE TABLE utilisateur (
    id                       BIGINT AUTO_INCREMENT PRIMARY KEY,
    matricule                VARCHAR(20),
    nom                      VARCHAR(120) NOT NULL,
    login                    VARCHAR(60)  NOT NULL,
    mot_de_passe             VARCHAR(160) NOT NULL,
    role                     VARCHAR(20)  NOT NULL,
    region_id                BIGINT,
    province_id              BIGINT,
    email                    VARCHAR(160),
    actif                    BOOLEAN NOT NULL DEFAULT TRUE,
    mot_de_passe_temporaire  BOOLEAN NOT NULL DEFAULT FALSE,
    derniere_connexion       TIMESTAMP NULL,
    CONSTRAINT uq_utilisateur_login UNIQUE (login),
    CONSTRAINT uq_utilisateur_matricule UNIQUE (matricule),
    CONSTRAINT fk_utilisateur_region FOREIGN KEY (region_id) REFERENCES region (id),
    CONSTRAINT fk_utilisateur_province FOREIGN KEY (province_id) REFERENCES province (id),
    CONSTRAINT ck_utilisateur_role CHECK (role IN ('ADMIN', 'PROVINCIAL', 'REGIONAL'))
);

CREATE TABLE droit (
    code         VARCHAR(60) PRIMARY KEY,
    description  VARCHAR(255) NOT NULL
);

CREATE TABLE role_droit (
    role        VARCHAR(20) NOT NULL,
    droit_code  VARCHAR(60) NOT NULL,
    PRIMARY KEY (role, droit_code),
    CONSTRAINT fk_role_droit_droit FOREIGN KEY (droit_code) REFERENCES droit (code)
);

INSERT INTO droit (code, description) VALUES
    ('UTILISATEUR_GERER',   'Créer et modifier les utilisateurs'),
    ('PERIODE_GERER',       'Créer, clôturer et rouvrir les périodes'),
    ('CENTRE_GERER',        'Ajouter et modifier les centres et leurs activités'),
    ('CENTRE_LIRE',         'Consulter les centres et leurs activités'),
    ('STATISTIQUE_SAISIR',  'Saisir et soumettre les statistiques'),
    ('STATISTIQUE_VERIFIER','Valider ou renvoyer les statistiques soumises'),
    ('STATISTIQUE_LIRE',    'Consulter et exporter les statistiques');

INSERT INTO role_droit (role, droit_code) VALUES
    ('ADMIN', 'UTILISATEUR_GERER'),
    ('ADMIN', 'PERIODE_GERER'),
    ('ADMIN', 'CENTRE_GERER'),
    ('ADMIN', 'CENTRE_LIRE'),
    ('ADMIN', 'STATISTIQUE_VERIFIER'),
    ('ADMIN', 'STATISTIQUE_LIRE'),
    ('PROVINCIAL', 'CENTRE_GERER'),
    ('PROVINCIAL', 'CENTRE_LIRE'),
    ('PROVINCIAL', 'STATISTIQUE_SAISIR'),
    ('PROVINCIAL', 'STATISTIQUE_LIRE'),
    ('REGIONAL', 'CENTRE_LIRE'),
    ('REGIONAL', 'STATISTIQUE_LIRE');
