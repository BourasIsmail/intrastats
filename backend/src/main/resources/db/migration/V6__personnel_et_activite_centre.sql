-- Personnel (remplace l'ancienne table personnel). Un agent est rattaché à une province (périmètre d'accès)
-- et, facultativement, à un centre.
CREATE TABLE personnel (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    matricule           VARCHAR(20),
    nom                 VARCHAR(60)  NOT NULL,
    prenom              VARCHAR(60)  NOT NULL,
    cin                 VARCHAR(20),
    date_naissance      DATE,
    lieu_naissance      VARCHAR(100),
    sexe                VARCHAR(1),
    date_recrutement    DATE,
    fonction            VARCHAR(60),
    autre_fonction      VARCHAR(60),
    province_id         BIGINT NOT NULL,
    centre_id           BIGINT,
    adresse             VARCHAR(400),
    telephone           VARCHAR(50),
    situation_familiale VARCHAR(20),
    nb_enfants          INT NOT NULL DEFAULT 0,
    niveau_scolaire     VARCHAR(40),
    actif               BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_personnel_province FOREIGN KEY (province_id) REFERENCES province (id),
    CONSTRAINT fk_personnel_centre FOREIGN KEY (centre_id) REFERENCES centre (id),
    CONSTRAINT ck_personnel_sexe CHECK (sexe IS NULL OR sexe IN ('M', 'F'))
);
CREATE INDEX idx_personnel_province ON personnel (province_id);
CREATE INDEX idx_personnel_centre ON personnel (centre_id);
CREATE INDEX idx_personnel_matricule ON personnel (matricule);
CREATE INDEX idx_personnel_cin ON personnel (cin);

-- Activité d'un centre : responsable (agent), superficie, précision de gestion, convention de partenariat.
ALTER TABLE centre_activite ADD COLUMN responsable_id BIGINT;
ALTER TABLE centre_activite ADD COLUMN superficie INT;
ALTER TABLE centre_activite ADD COLUMN gestion_autre VARCHAR(200);
ALTER TABLE centre_activite ADD COLUMN date_convention DATE;
ALTER TABLE centre_activite ADD CONSTRAINT fk_ca_responsable FOREIGN KEY (responsable_id) REFERENCES personnel (id);

-- Catalogue : une activité retirée du catalogue n'est plus proposée, mais son historique reste.
ALTER TABLE activite ADD COLUMN actif BOOLEAN NOT NULL DEFAULT TRUE;

INSERT INTO droit (code, description) VALUES
    ('PERSONNEL_LIRE',   'Consulter le personnel de son périmètre'),
    ('PERSONNEL_GERER',  'Ajouter et modifier le personnel de son périmètre'),
    ('ACTIVITE_GERER',   'Gérer le catalogue des activités');

INSERT INTO role_droit (role, droit_code) VALUES
    ('ADMIN', 'PERSONNEL_LIRE'),
    ('ADMIN', 'PERSONNEL_GERER'),
    ('ADMIN', 'ACTIVITE_GERER'),
    ('PROVINCIAL', 'PERSONNEL_LIRE'),
    ('PROVINCIAL', 'PERSONNEL_GERER');
