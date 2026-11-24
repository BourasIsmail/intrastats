-- Hiérarchie territoriale : région -> province -> commune
-- (les indicateurs de pauvreté des anciennes tables sont ignorés pour l'instant : futur module territoire_indicateur)

CREATE TABLE region (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom_fr  VARCHAR(120) NOT NULL,
    nom_ar  VARCHAR(160)
);

CREATE TABLE province (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom_fr     VARCHAR(120) NOT NULL,
    nom_ar     VARCHAR(160),
    region_id  BIGINT NOT NULL,
    CONSTRAINT fk_province_region FOREIGN KEY (region_id) REFERENCES region (id)
);
CREATE INDEX idx_province_region ON province (region_id);

CREATE TABLE commune (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    nom_fr       VARCHAR(200) NOT NULL,
    nom_ar       VARCHAR(250),
    province_id  BIGINT NOT NULL,
    indh         BOOLEAN NOT NULL DEFAULT FALSE,
    latitude     DECIMAL(10, 7),
    longitude    DECIMAL(10, 7),
    code_arcgis  INT,
    CONSTRAINT fk_commune_province FOREIGN KEY (province_id) REFERENCES province (id),
    CONSTRAINT uq_commune_arcgis UNIQUE (code_arcgis)
);
CREATE INDEX idx_commune_province ON commune (province_id);
