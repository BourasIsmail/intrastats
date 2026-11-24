-- Centres (fusion des anciennes tables locaux et centres), catalogue d'activités et offre par centre.
-- Les anciennes colonnes en doublon (latitu/longit, superficie2, nom2, addr2...) ne sont pas reprises.

CREATE TABLE centre (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    code             VARCHAR(30)  NOT NULL,
    nom_fr           VARCHAR(250) NOT NULL,
    nom_ar           VARCHAR(250),
    categorie        VARCHAR(50),
    type             VARCHAR(50),
    province_id      BIGINT NOT NULL,
    commune_id       BIGINT,
    milieu           VARCHAR(20),
    adresse          VARCHAR(255),
    latitude         DECIMAL(10, 7),
    longitude        DECIMAL(10, 7),
    position_exacte  BOOLEAN NOT NULL DEFAULT FALSE,
    date_construction DATE,
    telephone        VARCHAR(50),
    responsable      VARCHAR(160),
    created_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by       BIGINT,
    updated_by       BIGINT,
    CONSTRAINT uq_centre_code UNIQUE (code),
    CONSTRAINT fk_centre_province FOREIGN KEY (province_id) REFERENCES province (id),
    CONSTRAINT fk_centre_commune FOREIGN KEY (commune_id) REFERENCES commune (id)
);
CREATE INDEX idx_centre_province ON centre (province_id);

CREATE TABLE centre_foncier (
    centre_id           BIGINT PRIMARY KEY,
    propriete           VARCHAR(50),
    situation_fonciere  VARCHAR(60),
    proprietaire        VARCHAR(160),
    loyer_mensuel       DECIMAL(12, 2),
    charges_locatives   DECIMAL(12, 2),
    numero_contrat      VARCHAR(80),
    date_contrat        DATE,
    avenant             VARCHAR(60),
    CONSTRAINT fk_centre_foncier_centre FOREIGN KEY (centre_id) REFERENCES centre (id)
);

CREATE TABLE centre_equipement (
    centre_id        BIGINT PRIMARY KEY,
    eau              BOOLEAN NOT NULL DEFAULT FALSE,
    electricite      BOOLEAN NOT NULL DEFAULT FALSE,
    assainissement   BOOLEAN NOT NULL DEFAULT FALSE,
    telephone        BOOLEAN NOT NULL DEFAULT FALSE,
    internet         BOOLEAN NOT NULL DEFAULT FALSE,
    nb_ordinateurs   INT NOT NULL DEFAULT 0,
    nb_imprimantes   INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_centre_equipement_centre FOREIGN KEY (centre_id) REFERENCES centre (id)
);

-- Hiérarchie des activités : programme -> axe -> catégorie -> activité
CREATE TABLE programme (
    id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom  VARCHAR(120) NOT NULL
);

CREATE TABLE axe (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom           VARCHAR(120) NOT NULL,
    programme_id  BIGINT NOT NULL,
    CONSTRAINT fk_axe_programme FOREIGN KEY (programme_id) REFERENCES programme (id)
);

CREATE TABLE activite_categorie (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom     VARCHAR(150) NOT NULL,
    axe_id  BIGINT NOT NULL,
    CONSTRAINT fk_categorie_axe FOREIGN KEY (axe_id) REFERENCES axe (id)
);

CREATE TABLE activite (
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom              VARCHAR(250) NOT NULL,
    abreviation      VARCHAR(20),
    description      VARCHAR(255),
    categorie_id     BIGINT NOT NULL,
    type_activite    VARCHAR(20) NOT NULL DEFAULT 'PRINCIPALE',
    personnes_cibles VARCHAR(60),
    priorite         INT,
    CONSTRAINT fk_activite_categorie FOREIGN KEY (categorie_id) REFERENCES activite_categorie (id)
);

-- Activité ouverte dans un centre. date_cessation NULL = sans fin (remplace l'ancien 2222-12-31).
CREATE TABLE centre_activite (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    centre_id       BIGINT NOT NULL,
    activite_id     BIGINT NOT NULL,
    responsable     VARCHAR(160),
    capacite        INT,
    date_ouverture  DATE NOT NULL,
    date_cessation  DATE,
    motif_cessation VARCHAR(255),
    gestion         VARCHAR(40),
    partenaire      VARCHAR(200),
    CONSTRAINT uq_centre_activite UNIQUE (centre_id, activite_id),
    CONSTRAINT fk_ca_centre FOREIGN KEY (centre_id) REFERENCES centre (id),
    CONSTRAINT fk_ca_activite FOREIGN KEY (activite_id) REFERENCES activite (id)
);
CREATE INDEX idx_ca_centre ON centre_activite (centre_id);

-- Salles d'une activité (remplace les colonnes salle1..salle6)
CREATE TABLE salle (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    centre_activite_id BIGINT NOT NULL,
    numero             INT NOT NULL,
    superficie         INT,
    CONSTRAINT fk_salle_ca FOREIGN KEY (centre_activite_id) REFERENCES centre_activite (id)
);
