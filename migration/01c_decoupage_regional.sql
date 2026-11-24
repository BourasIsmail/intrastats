-- ============================================================================================
-- Découpage régional : régions issues de regions_nv, rattachement des provinces corrigé.
--
-- Le rattachement déduit de la structure (délégation -> coordination régionale -> regions_nv.id_coordination)
-- suit l'ancien découpage : il est faux pour plusieurs provinces (Settat, Khénifra, Tata, Errachidia, Dakhla...).
-- Ce script remplace la région de chaque province connue par celle du découpage régional de 2015 (12 régions).
-- Chaque ligne n'est appliquée que si l'identifiant de province ET sa délégation correspondent à l'export
-- examiné (id, id_delegation) : sur une autre base, rien n'est modifié.
--
-- À VÉRIFIER par l'institution : colonnes (province, délégation, région).
-- Les changements sont listés dans migration_rapport (catégorie province_region_corrigee).
-- ============================================================================================
SET NAMES utf8mb4;
USE __NEW__;

CREATE TEMPORARY TABLE tmp_decoupage (province_id INT PRIMARY KEY, delegation_id INT NOT NULL, region_id INT NOT NULL);
INSERT INTO tmp_decoupage (province_id, delegation_id, region_id) VALUES
    (1, 129, 5),
    (2, 144, 6),
    (3, 118, 6),
    (4, 141, 7),
    (5, 111, 3),
    (6, 122, 3),
    (7, 168, 3),
    (9, 126, 4),
    (10, 146, 4),
    (11, 112, 6),
    (12, 136, 6),
    (13, 108, 6),
    (14, 231, 6),
    (15, 103, 6),
    (18, 102, 6),
    (19, 172, 6),
    (20, 124, 10),
    (21, 148, 10),
    (22, 152, 9),
    (23, 158, 10),
    (24, 159, 11),
    (25, 160, 11),
    (26, 130, 11),
    (27, 114, 7),
    (28, 117, 7),
    (29, 121, 7),
    (30, 132, 7),
    (31, 116, 3),
    (32, 120, 8),
    (33, 125, 3),
    (34, 128, 5),
    (35, 134, 3),
    (36, 123, 2),
    (37, 137, 2),
    (38, 139, 2),
    (39, 162, 2),
    (40, 163, 2),
    (41, 171, 2),
    (42, 115, 12),
    (43, 246, 12),
    (44, 127, 4),
    (45, 140, 4),
    (46, 142, 4),
    (48, 154, 4),
    (49, 164, 9),
    (50, 165, 9),
    (51, 138, 8),
    (52, 166, 8),
    (53, 151, 9),
    (54, 156, 9),
    (55, 107, 5),
    (56, 109, 5),
    (57, 113, 1),
    (58, 131, 1),
    (59, 167, 1),
    (60, 155, 1),
    (61, 104, 1),
    (62, 150, 3),
    (63, 153, 3),
    (64, 110, 6),
    (66, 101, 9),
    (67, 119, 7),
    (68, 169, 3),
    (69, 149, 1),
    (70, 229, 6),
    (71, 232, 6),
    (72, 170, 1),
    (73, 233, 6),
    (74, 259, 8),
    (76, 267, 8),
    (77, 270, 7),
    (78, 272, 6),
    (79, 274, 1),
    (80, 283, 10),
    (81, 266, 2),
    (82, 261, 11),
    (83, 260, 5),
    (84, 257, 4),
    (85, 250, 7),
    (86, 249, 6),
    (87, 237, 2),
    (88, 235, 6);

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'province_region_corrigee', np.id,
       CONCAT('province « ', np.nom_fr, ' » : région ', np.region_id, ' (déduite de la structure) remplacée par ', d.region_id, ' (découpage 2015)')
FROM tmp_decoupage d
JOIN __NEW__.province np ON np.id = d.province_id
JOIN __OLD__.provinces op ON op.id = d.province_id AND op.id_delegation = d.delegation_id
WHERE np.region_id <> d.region_id AND EXISTS (SELECT 1 FROM __NEW__.region r WHERE r.id = d.region_id);

UPDATE __NEW__.province np
JOIN tmp_decoupage d ON d.province_id = np.id
JOIN __OLD__.provinces op ON op.id = d.province_id AND op.id_delegation = d.delegation_id
SET np.region_id = d.region_id
WHERE EXISTS (SELECT 1 FROM __NEW__.region r WHERE r.id = d.region_id);

-- Noms abîmés par l'ancien encodage (accents perdus dans l'export examiné).
UPDATE __NEW__.province SET nom_fr = 'Salé' WHERE id = 46 AND nom_fr = 'Sal';
UPDATE __NEW__.province SET nom_fr = 'Béni Mellal' WHERE id = 56 AND nom_fr LIKE 'B_ni Mellal';

DROP TEMPORARY TABLE tmp_decoupage;
