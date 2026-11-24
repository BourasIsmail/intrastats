-- Périodes, saisie par province et statistiques mensuelles.

CREATE TABLE periode (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    mois              INT NOT NULL,
    annee             INT NOT NULL,
    annee_stat        VARCHAR(4) NOT NULL,
    statut            VARCHAR(10) NOT NULL DEFAULT 'OUVERTE',
    comptee_en_stats  BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_periode UNIQUE (annee, mois),
    CONSTRAINT ck_periode_mois CHECK (mois BETWEEN 1 AND 12),
    CONSTRAINT ck_periode_statut CHECK (statut IN ('OUVERTE', 'CLOTUREE'))
);

-- Etat de la saisie d'une province pour une période : BROUILLON -> SOUMIS -> VALIDE (ou A_CORRIGER)
CREATE TABLE saisie_province (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    province_id   BIGINT NOT NULL,
    periode_id    BIGINT NOT NULL,
    statut        VARCHAR(12) NOT NULL DEFAULT 'BROUILLON',
    motif_renvoi  VARCHAR(500),
    soumise_le    TIMESTAMP NULL,
    soumise_par   BIGINT,
    decision_le   TIMESTAMP NULL,
    decision_par  BIGINT,
    CONSTRAINT uq_saisie UNIQUE (province_id, periode_id),
    CONSTRAINT fk_saisie_province FOREIGN KEY (province_id) REFERENCES province (id),
    CONSTRAINT fk_saisie_periode FOREIGN KEY (periode_id) REFERENCES periode (id),
    CONSTRAINT ck_saisie_statut CHECK (statut IN ('BROUILLON', 'SOUMIS', 'VALIDE', 'A_CORRIGER'))
);

-- Une ligne par activité de centre et par période.
-- Les indicateurs sont NULL tant qu'ils ne sont pas saisis (brouillon).
CREATE TABLE statistique (
    id                    BIGINT AUTO_INCREMENT PRIMARY KEY,
    centre_activite_id    BIGINT NOT NULL,
    periode_id            BIGINT NOT NULL,
    masculins             INT,
    feminins              INT,
    alphabetisation       INT,
    agents_titulaires     INT,
    agents_vacataires     INT,
    agents_volontaires    INT,
    agents_associations   INT,
    agents_autre_dep      INT,
    etat_centre           VARCHAR(25) NOT NULL DEFAULT 'ACTIF',
    motif                 VARCHAR(500),
    updated_at            TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by            BIGINT,
    CONSTRAINT uq_statistique UNIQUE (centre_activite_id, periode_id),
    CONSTRAINT fk_stat_ca FOREIGN KEY (centre_activite_id) REFERENCES centre_activite (id),
    CONSTRAINT fk_stat_periode FOREIGN KEY (periode_id) REFERENCES periode (id),
    CONSTRAINT ck_stat_etat CHECK (etat_centre IN ('ACTIF', 'FERME_TEMPORAIREMENT', 'FERME_DEFINITIVEMENT'))
);
CREATE INDEX idx_stat_periode ON statistique (periode_id);

CREATE TABLE journal_audit (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    horodatage      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    utilisateur_id  BIGINT,
    action          VARCHAR(60) NOT NULL,
    entite          VARCHAR(60) NOT NULL,
    entite_id       VARCHAR(60),
    details         VARCHAR(1000)
);
CREATE INDEX idx_audit_entite ON journal_audit (entite, entite_id);
