-- ============================================================================================
-- Migration de l'ancienne base (__OLD__) vers le nouveau schéma (__NEW__).
--
-- Prérequis : le nouveau schéma existe (migrations Flyway V1 à V6 appliquées, SANS le jeu d'exemples V100)
-- et les deux bases sont sur le même serveur MySQL 8. Lancer avec migration/migrate.sh.
--
-- Principes :
--   * rien n'est modifié dans l'ancienne base ;
--   * les identifiants sont conservés pour le territoire, les centres, les activités et les périodes ;
--   * les lignes inutilisables (orphelines, doublons, valeurs invalides) sont écartées ou corrigées,
--     et listées dans la table __NEW__.migration_rapport : rien ne disparaît en silence ;
--   * le script peut être relancé après un `TRUNCATE` des tables du nouveau schéma (voir migrate.sh --reset).
-- ============================================================================================

SET SESSION sql_mode = 'NO_ENGINE_SUBSTITUTION';
SET NAMES utf8mb4;
USE __NEW__;

CREATE TABLE IF NOT EXISTS __NEW__.migration_rapport (
    id        INT AUTO_INCREMENT PRIMARY KEY,
    categorie VARCHAR(60)  NOT NULL,
    ancien_id VARCHAR(60),
    detail    VARCHAR(500)
);
TRUNCATE TABLE __NEW__.migration_rapport;

-- --------------------------------------------------------------------------------------------
-- 1. Territoire
-- --------------------------------------------------------------------------------------------
-- Régions : table regions_nv (le nouveau découpage en 12 régions).
INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'region_doublon', id, CONCAT(COUNT(*), ' lignes avec le même identifiant : la première est conservée')
FROM __OLD__.regions_nv WHERE id > 0 GROUP BY id HAVING COUNT(*) > 1;

-- Parmi des lignes au même identifiant, on garde celle qui a le nom arabe (puis le nom le plus court).
INSERT INTO __NEW__.region (id, nom_fr, nom_ar)
SELECT r.id, TRIM(r.region), NULLIF(TRIM(r.region_ar), '')
FROM (SELECT g.*, ROW_NUMBER() OVER (PARTITION BY g.id ORDER BY CHAR_LENGTH(g.region_ar) DESC, CHAR_LENGTH(g.region)) AS rn
      FROM __OLD__.regions_nv g WHERE g.id > 0) r
WHERE r.rn = 1 AND TRIM(r.region) <> '';

-- Région et nom arabe d'une province : table delegations (une délégation par province, liée par provinces.id_delegation).
-- delegations.id_region_nv désigne la région de regions_nv ; delegation_ar est le nom arabe.
-- (provinces.id_region n'est pas renseigné et suit l'ancien découpage : il n'est pas utilisé.)
CREATE TEMPORARY TABLE tmp_province_region AS
SELECT p.id AS province_id,
       (SELECT d.id_region_nv FROM __OLD__.delegations d
        WHERE d.id = p.id_delegation AND EXISTS (SELECT 1 FROM __NEW__.region nr WHERE nr.id = d.id_region_nv)) AS region_id,
       (SELECT NULLIF(TRIM(d.delegation_ar), '') FROM __OLD__.delegations d WHERE d.id = p.id_delegation) AS nom_ar
FROM __OLD__.provinces p;

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'province_sans_region', p.id, CONCAT('province « ', p.province, ' » : aucune délégation avec une région valide pour id_delegation ', IFNULL(p.id_delegation, 'NULL'), ', non migrée')
FROM __OLD__.provinces p JOIN tmp_province_region t ON t.province_id = p.id
WHERE t.region_id IS NULL;

INSERT INTO __NEW__.province (id, nom_fr, nom_ar, region_id)
SELECT p.id, TRIM(p.province), t.nom_ar, t.region_id
FROM __OLD__.provinces p JOIN tmp_province_region t ON t.province_id = p.id
WHERE p.province IS NOT NULL AND TRIM(p.province) <> '' AND t.region_id IS NOT NULL;

-- Noms abîmés par l'ancien encodage (accents perdus dans l'export examiné).
UPDATE __NEW__.province SET nom_fr = 'Salé' WHERE id = 46 AND nom_fr = 'Sal';
UPDATE __NEW__.province SET nom_fr = 'Béni Mellal' WHERE id = 56 AND nom_fr LIKE 'B_ni Mellal';

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'commune_sans_province', c.id, CONCAT('commune « ', c.commune, ' » : province ', IFNULL(c.id_province, 'NULL'), ' absente, non migrée')
FROM __OLD__.communes c
WHERE NOT EXISTS (SELECT 1 FROM __NEW__.province p WHERE p.id = c.id_province);

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'commune_sans_nom', c.id, CONCAT('commune de la province ', c.id_province, ' sans nom : non migrée')
FROM __OLD__.communes c
WHERE (c.commune IS NULL OR TRIM(c.commune) = '') AND EXISTS (SELECT 1 FROM __NEW__.province p WHERE p.id = c.id_province);

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'commune_code_arcgis_double', c.id, CONCAT('code ArcGIS ', c.code_arcgis, ' déjà utilisé : code non repris')
FROM (SELECT id, code_arcgis, ROW_NUMBER() OVER (PARTITION BY code_arcgis ORDER BY id) AS rn
      FROM __OLD__.communes WHERE code_arcgis <> 0) c
WHERE c.rn > 1;

INSERT INTO __NEW__.commune (id, nom_fr, province_id, indh, latitude, longitude, code_arcgis)
SELECT c.id, TRIM(c.commune), c.id_province,
       UPPER(c.INDH) IN ('O', 'Y', '1', 'T'),
       CASE WHEN c.latitude  <> 0 AND ABS(c.latitude)  <= 90  THEN ROUND(c.latitude, 7)  END,
       CASE WHEN c.longitude <> 0 AND ABS(c.longitude) <= 180 THEN ROUND(c.longitude, 7) END,
       CASE WHEN c.code_arcgis <> 0 AND c.rn = 1 THEN c.code_arcgis END
FROM (SELECT cc.*, ROW_NUMBER() OVER (PARTITION BY cc.code_arcgis ORDER BY cc.id) AS rn FROM __OLD__.communes cc) c
WHERE c.commune IS NOT NULL AND TRIM(c.commune) <> ''
  AND EXISTS (SELECT 1 FROM __NEW__.province p WHERE p.id = c.id_province);

-- --------------------------------------------------------------------------------------------
-- 2. Centres (locaux qui ont une ligne dans « centres ») + foncier + équipements
-- --------------------------------------------------------------------------------------------
INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'local_non_centre', l.id, CONCAT('local « ', l.nom, ' » (', l.categorie, ') : pas un centre, non migré')
FROM __OLD__.locaux l WHERE NOT EXISTS (SELECT 1 FROM __OLD__.centres c WHERE c.id = l.id);

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'centre_sans_local', c.id, CONCAT('ligne de « centres » sans local correspondant (type « ', IFNULL(c.type, ''), ' ») : ni nom ni province, non migré')
FROM __OLD__.centres c WHERE NOT EXISTS (SELECT 1 FROM __OLD__.locaux l WHERE l.id = c.id);

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'centre_sans_province', l.id, CONCAT('centre « ', l.nom, ' » : province ', l.id_province, ' absente, non migré')
FROM __OLD__.locaux l JOIN __OLD__.centres c ON c.id = l.id
WHERE NOT EXISTS (SELECT 1 FROM __NEW__.province p WHERE p.id = l.id_province);

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'centre_code_corrige', x.id, CONCAT('code « ', IFNULL(x.code, ''), ' » vide ou déjà utilisé : remplacé par ', x.nouveau)
FROM (SELECT l.id, l.code,
             CASE WHEN TRIM(l.code) = '' THEN CONCAT('C-', l.id)
                  WHEN ROW_NUMBER() OVER (PARTITION BY TRIM(l.code) ORDER BY l.id) > 1 THEN CONCAT(TRIM(l.code), '-', l.id)
             END AS nouveau
      FROM __OLD__.locaux l JOIN __OLD__.centres c ON c.id = l.id) x
WHERE x.nouveau IS NOT NULL;

INSERT INTO __NEW__.centre (id, code, nom_fr, nom_ar, categorie, type, province_id, commune_id, milieu, adresse,
                            latitude, longitude, position_exacte, date_construction, telephone, created_at, updated_at)
SELECT l.id,
       CASE WHEN TRIM(l.code) = '' THEN CONCAT('C-', l.id)
            WHEN ROW_NUMBER() OVER (PARTITION BY TRIM(l.code) ORDER BY l.id) > 1 THEN CONCAT(TRIM(l.code), '-', l.id)
            ELSE TRIM(l.code) END,
       TRIM(l.nom),
       NULLIF(TRIM(c.nom_ar), ''),
       NULLIF(TRIM(l.categorie), ''),
       COALESCE(NULLIF(TRIM(c.type), ''), NULLIF(TRIM(l.type_centre), ''), NULLIF(TRIM(l.type), '')),
       l.id_province,
       (SELECT cm.id FROM __NEW__.commune cm WHERE cm.id = l.id_commune),
       NULLIF(LOWER(TRIM(l.milieu)), ''),
       COALESCE(NULLIF(TRIM(l.addresse), ''), NULLIF(TRIM(l.addresse0), ''), NULLIF(TRIM(l.addr2), '')),
       CASE WHEN l.latitude <> 0 AND l.longitude <> 0 AND ABS(l.latitude) <= 90 AND ABS(l.longitude) <= 180 THEN ROUND(l.latitude, 7)
            WHEN l.latitu <> 0 AND l.longit <> 0 AND ABS(l.latitu) <= 90 AND ABS(l.longit) <= 180 THEN ROUND(l.latitu, 7) END,
       CASE WHEN l.latitude <> 0 AND l.longitude <> 0 AND ABS(l.latitude) <= 90 AND ABS(l.longitude) <= 180 THEN ROUND(l.longitude, 7)
            WHEN l.latitu <> 0 AND l.longit <> 0 AND ABS(l.latitu) <= 90 AND ABS(l.longit) <= 180 THEN ROUND(l.longit, 7) END,
       l.exact <> 0,
       CASE WHEN l.date_construction >= '1900-01-01' THEN l.date_construction END,
       NULLIF(TRIM(l.tel), ''),
       CASE WHEN l.date_saisie >= '1990-01-01' THEN l.date_saisie ELSE NOW() END,
       CASE WHEN l.date_modif  >= '1990-01-01' THEN l.date_modif WHEN l.date_saisie >= '1990-01-01' THEN l.date_saisie ELSE NOW() END
FROM __OLD__.locaux l
JOIN __OLD__.centres c ON c.id = l.id
WHERE EXISTS (SELECT 1 FROM __NEW__.province p WHERE p.id = l.id_province);

INSERT INTO __NEW__.centre_foncier (centre_id, propriete, situation_fonciere, proprietaire, loyer_mensuel, charges_locatives,
                                    numero_contrat, date_contrat, avenant)
SELECT l.id, NULLIF(TRIM(l.propriete), ''), NULLIF(TRIM(l.situation_fonciere), ''), NULLIF(TRIM(l.nom_proprietaire), ''),
       NULLIF(l.montant_loyer, 0), NULLIF(l.charge_locative, 0), LEFT(NULLIF(TRIM(l.num_contrat), ''), 80),
       CASE WHEN l.date_contrat >= '1900-01-01' THEN l.date_contrat END, NULLIF(TRIM(l.avenant), '')
FROM __OLD__.locaux l
WHERE EXISTS (SELECT 1 FROM __NEW__.centre ce WHERE ce.id = l.id);

INSERT INTO __NEW__.centre_equipement (centre_id, eau, electricite, assainissement, telephone, internet, nb_ordinateurs, nb_imprimantes)
SELECT l.id, l.eau <> 0, l.electricite <> 0, l.assinissement <> 0, l.telephone <> 0, l.internet > 0,
       GREATEST(l.nb_pc, 0), GREATEST(l.nb_imprimantes, 0)
FROM __OLD__.locaux l
WHERE EXISTS (SELECT 1 FROM __NEW__.centre ce WHERE ce.id = l.id);

-- --------------------------------------------------------------------------------------------
-- 3. Catalogue d'activités : programme -> axe -> catégorie -> activité
--    (la hiérarchie était en texte libre dans activites ; les valeurs vides deviennent « Non classé »)
-- --------------------------------------------------------------------------------------------
INSERT INTO __NEW__.programme (nom)
SELECT DISTINCT COALESCE(NULLIF(TRIM(programme), ''), 'Non classé') FROM __OLD__.activites;

INSERT INTO __NEW__.axe (nom, programme_id)
SELECT d.axe, p.id
FROM (SELECT DISTINCT COALESCE(NULLIF(TRIM(programme), ''), 'Non classé') AS programme,
                      COALESCE(NULLIF(TRIM(axe), ''), 'Non classé') AS axe FROM __OLD__.activites) d
JOIN __NEW__.programme p ON p.nom = d.programme;

INSERT INTO __NEW__.activite_categorie (nom, axe_id)
SELECT d.categorie, a.id
FROM (SELECT DISTINCT COALESCE(NULLIF(TRIM(programme), ''), 'Non classé') AS programme,
                      COALESCE(NULLIF(TRIM(axe), ''), 'Non classé') AS axe,
                      COALESCE(NULLIF(TRIM(categorie2), ''), NULLIF(TRIM(categorie), ''), 'Non classé') AS categorie
      FROM __OLD__.activites) d
JOIN __NEW__.programme p ON p.nom = d.programme
JOIN __NEW__.axe a ON a.programme_id = p.id AND a.nom = d.axe;

INSERT INTO __NEW__.activite (id, nom, abreviation, description, categorie_id, type_activite, personnes_cibles, priorite)
SELECT a.id, TRIM(a.activite), NULLIF(TRIM(a.abrev), ''), NULLIF(TRIM(a.description), ''), cat.id,
       UPPER(COALESCE(NULLIF(TRIM(a.type_activite), ''), 'principale')), NULLIF(TRIM(a.personnes_cibles), ''), NULLIF(a.priorite, 0)
FROM __OLD__.activites a
JOIN __NEW__.programme p ON p.nom = COALESCE(NULLIF(TRIM(a.programme), ''), 'Non classé')
JOIN __NEW__.axe ax ON ax.programme_id = p.id AND ax.nom = COALESCE(NULLIF(TRIM(a.axe), ''), 'Non classé')
JOIN __NEW__.activite_categorie cat ON cat.axe_id = ax.id
 AND cat.nom = COALESCE(NULLIF(TRIM(a.categorie2), ''), NULLIF(TRIM(a.categorie), ''), 'Non classé')
WHERE TRIM(a.activite) <> '';

-- --------------------------------------------------------------------------------------------
-- 3b. Personnel (identifiants conservés : activite_centre.id_responsable y fait référence)
--     Province = celle du centre (id_local) si connu, sinon celle dont la structure est la délégation (id_structure).
--     Non repris : som, verif, verif1, prov, id_str2, id_activite (sens non confirmé).
-- --------------------------------------------------------------------------------------------
CREATE TEMPORARY TABLE tmp_personnel AS
SELECT p.*, COALESCE(c.province_id, (SELECT MIN(op.id) FROM __OLD__.provinces op WHERE op.id_delegation = p.id_structure)) AS province_resolue,
       c.id AS centre_resolu
FROM __OLD__.personnel p
LEFT JOIN __NEW__.centre c ON c.id = p.id_local;

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'personnel_sans_nom', t.id, 'agent sans nom : non migré' FROM tmp_personnel t WHERE TRIM(t.nom) = '';

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'personnel_sans_province', t.id, CONCAT('agent « ', t.nom, ' » : ni centre ni structure reconnus, non migré')
FROM tmp_personnel t
WHERE TRIM(t.nom) <> '' AND (t.province_resolue IS NULL OR NOT EXISTS (SELECT 1 FROM __NEW__.province np WHERE np.id = t.province_resolue));

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'personnel_cin_doublon', MIN(t.id), CONCAT('CIN ', t.cin, ' : ', COUNT(*), ' agents')
FROM tmp_personnel t WHERE TRIM(t.cin) <> '' GROUP BY t.cin HAVING COUNT(*) > 1;

INSERT INTO __NEW__.personnel (id, matricule, nom, prenom, cin, date_naissance, lieu_naissance, sexe, date_recrutement, fonction,
                               autre_fonction, province_id, centre_id, adresse, telephone, situation_familiale, nb_enfants,
                               niveau_scolaire, actif)
SELECT t.id, NULLIF(TRIM(t.matricule), ''), TRIM(t.nom), COALESCE(NULLIF(TRIM(t.prenom), ''), '-'), NULLIF(TRIM(t.cin), ''),
       CASE WHEN t.date_naissance >= '1900-01-01' AND t.date_naissance <= CURDATE() THEN t.date_naissance END,
       NULLIF(TRIM(t.lieu_naissance), ''),
       CASE UPPER(TRIM(t.sexe)) WHEN 'M' THEN 'M' WHEN 'F' THEN 'F' END,
       CASE WHEN t.date_recrutement >= '1950-01-01' AND t.date_recrutement <= CURDATE() THEN t.date_recrutement END,
       NULLIF(TRIM(t.fonction), ''), NULLIF(TRIM(t.autre_fonction), ''), t.province_resolue, t.centre_resolu,
       LEFT(CONCAT_WS(' — ', NULLIF(TRIM(t.adresse), ''), NULLIF(TRIM(t.adresse2), '')), 400),
       NULLIF(TRIM(t.tel), ''), NULLIF(TRIM(t.situation_familiale), ''), GREATEST(0, LEAST(30, COALESCE(t.nb_enfants, 0))),
       NULLIF(TRIM(t.niveau_scolaire), ''), TRUE
FROM tmp_personnel t
WHERE TRIM(t.nom) <> '' AND t.province_resolue IS NOT NULL
  AND EXISTS (SELECT 1 FROM __NEW__.province np WHERE np.id = t.province_resolue);

-- --------------------------------------------------------------------------------------------
-- 4. Activités ouvertes dans les centres (+ salles)
-- --------------------------------------------------------------------------------------------
INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'activite_centre_orpheline', ac.Id, CONCAT('centre ', ac.id_centre, ' / activité ', ac.id_activite, ' : centre ou activité absent(e), ligne écartée')
FROM __OLD__.activite_centre ac
WHERE NOT EXISTS (SELECT 1 FROM __NEW__.centre c WHERE c.id = ac.id_centre)
   OR NOT EXISTS (SELECT 1 FROM __NEW__.activite a WHERE a.id = ac.id_activite);

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'activite_centre_doublon', MIN(ac.Id), CONCAT('centre ', ac.id_centre, ' / activité ', ac.id_activite, ' : ', COUNT(*), ' lignes, seule la plus récente est conservée')
FROM __OLD__.activite_centre ac
WHERE EXISTS (SELECT 1 FROM __NEW__.centre c WHERE c.id = ac.id_centre) AND EXISTS (SELECT 1 FROM __NEW__.activite a WHERE a.id = ac.id_activite)
GROUP BY ac.id_centre, ac.id_activite HAVING COUNT(*) > 1;

CREATE TEMPORARY TABLE tmp_activite_centre AS
SELECT x.* FROM (
    SELECT ac.*, ROW_NUMBER() OVER (PARTITION BY ac.id_centre, ac.id_activite
                                    ORDER BY (ac.date_ouvert_act >= '1990-01-01') DESC, ac.date_ouvert_act DESC, ac.Id DESC) AS rn
    FROM __OLD__.activite_centre ac
    WHERE EXISTS (SELECT 1 FROM __NEW__.centre c WHERE c.id = ac.id_centre)
      AND EXISTS (SELECT 1 FROM __NEW__.activite a WHERE a.id = ac.id_activite)
) x WHERE x.rn = 1;

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'responsable_introuvable', t.Id, CONCAT('centre ', t.id_centre, ' / activité ', t.id_activite, ' : id_responsable ', t.id_responsable, ' absent du personnel migré, laissé vide')
FROM tmp_activite_centre t
WHERE t.id_responsable IS NOT NULL AND t.id_responsable <> 0
  AND NOT EXISTS (SELECT 1 FROM __NEW__.personnel pe WHERE pe.id = t.id_responsable);

-- Incohérences de l'ancienne base, corrigées ci-dessous et comptées ici (une ligne de rapport par catégorie).
INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'activite_fermee_sans_date', COUNT(*), 'activités fermées sans date de cessation valide : date de saisie utilisée (ou date du jour)'
FROM tmp_activite_centre t WHERE LOWER(t.etat) LIKE 'ferm%' AND NOT (t.date_cessation >= '1990-01-01' AND t.date_cessation < '2200-01-01') HAVING COUNT(*) > 0;
INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'activite_ouverte_avec_date', COUNT(*), 'activités « Ouvert » portant une date de cessation : l''état fait foi, activité laissée ouverte'
FROM tmp_activite_centre t WHERE LOWER(t.etat) LIKE 'ouver%' AND t.date_cessation >= '1990-01-01' AND t.date_cessation < '2200-01-01' HAVING COUNT(*) > 0;
INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'date_ouverture_inconnue', COUNT(*), 'activités sans date d''ouverture valide : 01/01/1970 utilisée, à corriger dans l''application'
FROM tmp_activite_centre t WHERE NOT (t.date_ouvert_act >= '1990-01-01') HAVING COUNT(*) > 0;
INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'convention_sans_association', COUNT(*), 'dates de convention sans association : date non reprise'
FROM tmp_activite_centre t WHERE t.date_conv >= '1990-01-01' AND TRIM(t.association) = '' AND TRIM(t.association2) = '' HAVING COUNT(*) > 0;

-- gestion : liste Entraide Nationale / Association / Cogestion / Autre ; toute autre valeur devient « Autre » avec le texte d'origine.
INSERT INTO __NEW__.centre_activite (centre_id, activite_id, responsable_id, capacite, superficie, date_ouverture, date_cessation,
                                     motif_cessation, gestion, gestion_autre, partenaire, date_convention)
SELECT t.id_centre, t.id_activite,
       (SELECT pe.id FROM __NEW__.personnel pe WHERE pe.id = t.id_responsable),
       NULLIF(t.capacite, 0), NULLIF(t.superficie, 0),
       CASE WHEN t.date_ouvert_act >= '1990-01-01' THEN DATE(t.date_ouvert_act) ELSE DATE '1970-01-01' END,
       -- L'état (Ouvert / Fermé) fait foi : fermé sans date valide -> date de saisie ; ouvert -> pas de date de cessation.
       CASE WHEN LOWER(t.etat) LIKE 'ferm%' THEN
                 COALESCE(CASE WHEN t.date_cessation >= '1990-01-01' AND t.date_cessation < '2200-01-01' THEN t.date_cessation END,
                          CASE WHEN t.date_saisie >= '1990-01-01' THEN t.date_saisie END, CURDATE())
            WHEN LOWER(t.etat) LIKE 'ouver%' THEN NULL
            ELSE CASE WHEN t.date_cessation >= '1990-01-01' AND t.date_cessation < '2200-01-01' THEN t.date_cessation END END,
       NULLIF(TRIM(t.motif), ''),
       CASE WHEN TRIM(t.gestion) = '' THEN NULL
            WHEN LOWER(t.gestion) LIKE '%entraide%' THEN 'ENTRAIDE_NATIONALE'
            WHEN LOWER(t.gestion) LIKE '%cogestion%' OR LOWER(t.gestion) LIKE '%co-gestion%' OR LOWER(t.gestion) LIKE '%co gestion%' THEN 'COGESTION'
            WHEN LOWER(t.gestion) LIKE '%association%' THEN 'ASSOCIATION'
            ELSE 'AUTRE' END,
       CASE WHEN TRIM(t.gestion) = '' OR LOWER(t.gestion) LIKE '%entraide%' OR LOWER(t.gestion) LIKE '%cogestion%'
                 OR LOWER(t.gestion) LIKE '%co-gestion%' OR LOWER(t.gestion) LIKE '%co gestion%' OR LOWER(t.gestion) LIKE '%association%' THEN NULL
            ELSE COALESCE(NULLIF(TRIM(t.gestion_autre), ''), TRIM(t.gestion)) END,
       COALESCE(NULLIF(TRIM(t.association), ''), NULLIF(TRIM(t.association2), '')),
       CASE WHEN t.date_conv >= '1990-01-01' AND t.date_conv <= CURDATE()
                 AND COALESCE(NULLIF(TRIM(t.association), ''), NULLIF(TRIM(t.association2), '')) IS NOT NULL THEN t.date_conv END
FROM tmp_activite_centre t;

INSERT INTO __NEW__.salle (centre_activite_id, numero, superficie)
SELECT ca.id, s.numero, s.superficie
FROM tmp_activite_centre t
JOIN __NEW__.centre_activite ca ON ca.centre_id = t.id_centre AND ca.activite_id = t.id_activite
JOIN (SELECT Id, 1 AS numero, salle1 AS superficie FROM __OLD__.activite_centre
      UNION ALL SELECT Id, 2, salle2 FROM __OLD__.activite_centre UNION ALL SELECT Id, 3, salle3 FROM __OLD__.activite_centre
      UNION ALL SELECT Id, 4, salle4 FROM __OLD__.activite_centre UNION ALL SELECT Id, 5, salle5 FROM __OLD__.activite_centre
      UNION ALL SELECT Id, 6, salle6 FROM __OLD__.activite_centre) s ON s.Id = t.Id
WHERE s.superficie > 0;

-- --------------------------------------------------------------------------------------------
-- 6. Utilisateurs
--    Rôle et périmètre sont déduits de la structure de rattachement :
--      * structure = délégation d'une province (provinces.id_delegation)  -> PROVINCIAL de cette province ;
--      * structure = coordination d'une région (regions_nv.id_coordination)  -> REGIONAL de cette région ;
--      * type contenant « admin », ou structure de type message_admin     -> ADMIN.
--      * toute autre structure (services centraux, divisions...) : compte non migré, listé dans le rapport.
--    Les mots de passe restent au format MySQL PASSWORD() : préfixe {mysql}, remplacés par bcrypt à la première connexion.
-- --------------------------------------------------------------------------------------------
CREATE TEMPORARY TABLE tmp_users AS
-- Textes convertis en utf8mb4 : l'ancienne table est en latin1 et MySQL refuse de mélanger ses colonnes avec des
-- messages accentués (erreur « Illegal mix of collations »).
SELECT u.id, CONVERT(NULLIF(TRIM(u.matricule), '') USING utf8mb4) AS matricule,
       CONVERT(COALESCE(NULLIF(TRIM(u.nom), ''), TRIM(u.login)) USING utf8mb4) AS nom,
       CONVERT(TRIM(u.login) USING utf8mb4) AS login, CONVERT(u.password USING utf8mb4) AS password,
       u.state, CONVERT(u.e_mail USING utf8mb4) AS e_mail, u.last_connexion_at,
       CASE WHEN (LOWER(u.type) LIKE '%admin%' OR su.type = 'message_admin') THEN 'ADMIN'
            WHEN pv.id IS NOT NULL THEN 'PROVINCIAL'
            WHEN rg.id IS NOT NULL THEN 'REGIONAL' END AS role,
       CASE WHEN (LOWER(u.type) LIKE '%admin%' OR su.type = 'message_admin') THEN NULL ELSE pv.id END AS province_id,
       CASE WHEN (LOWER(u.type) LIKE '%admin%' OR su.type = 'message_admin') OR pv.id IS NOT NULL THEN NULL ELSE rg.id END AS region_id,
       ROW_NUMBER() OVER (PARTITION BY TRIM(u.login) ORDER BY u.id) AS rn_login,
       ROW_NUMBER() OVER (PARTITION BY NULLIF(TRIM(u.matricule), '') ORDER BY u.id) AS rn_mat,
       COUNT(*) OVER (PARTITION BY u.password) AS nb_meme_hash
FROM __OLD__.users u
LEFT JOIN __OLD__.structures su ON su.id = u.id_structure
LEFT JOIN (SELECT MIN(p.id) AS id, p.id_delegation FROM __OLD__.provinces p
           WHERE EXISTS (SELECT 1 FROM __NEW__.province np WHERE np.id = p.id) GROUP BY p.id_delegation) pv
       ON pv.id_delegation = u.id_structure
LEFT JOIN (SELECT MIN(r.id) AS id, r.id_coordination FROM __OLD__.regions_nv r WHERE r.id > 0 GROUP BY r.id_coordination) rg
       ON rg.id_coordination = u.id_structure AND EXISTS (SELECT 1 FROM __NEW__.region nr WHERE nr.id = rg.id);

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'utilisateur_non_migre', t.id,
       CONCAT('compte « ', t.login, ' » : ',
              CASE WHEN t.login = '' THEN 'identifiant vide'
                   WHEN t.rn_login > 1 THEN 'identifiant en double'
                   WHEN t.role IS NULL THEN 'rôle ou périmètre introuvable (structure de rattachement inconnue) : à créer à la main'
                   WHEN t.password NOT REGEXP '^\\*[0-9A-Fa-f]{40}$' THEN 'mot de passe dans un format inconnu : à recréer'
              END)
FROM tmp_users t
WHERE t.login = '' OR t.rn_login > 1 OR t.role IS NULL OR t.password NOT REGEXP '^\\*[0-9A-Fa-f]{40}$';

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'utilisateur_matricule_double', t.id, CONCAT('matricule ', t.matricule, ' déjà utilisé : matricule non repris')
FROM tmp_users t WHERE t.matricule IS NOT NULL AND t.rn_mat > 1;

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'utilisateur_mot_de_passe_partage', t.id, CONCAT('compte « ', t.login, ' » partage son mot de passe avec ', t.nb_meme_hash - 1,
                                                           ' autre(s) : changement forcé à la première connexion')
FROM tmp_users t
WHERE t.nb_meme_hash > 1 AND t.rn_login = 1 AND t.role IS NOT NULL AND t.password REGEXP '^\\*[0-9A-Fa-f]{40}$';

INSERT INTO __NEW__.utilisateur (id, matricule, nom, login, mot_de_passe, role, region_id, province_id, email, actif,
                                 mot_de_passe_temporaire, derniere_connexion)
SELECT t.id, CASE WHEN t.rn_mat = 1 THEN t.matricule END, LEFT(t.nom, 120), LEFT(t.login, 60),
       CONCAT('{mysql}', UPPER(t.password)), t.role, t.region_id, t.province_id, LEFT(NULLIF(TRIM(t.e_mail), ''), 160),
       t.state = '1', t.nb_meme_hash > 1, t.last_connexion_at
FROM tmp_users t
WHERE t.login <> '' AND t.rn_login = 1 AND t.role IS NOT NULL AND t.password REGEXP '^\\*[0-9A-Fa-f]{40}$'
  AND t.login REGEXP '^[A-Za-z0-9._-]{3,60}$';

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'utilisateur_login_non_conforme', t.id, CONCAT('identifiant « ', t.login, ' » : caractères ou longueur non acceptés par le nouveau système, compte non migré')
FROM tmp_users t
WHERE t.login <> '' AND t.rn_login = 1 AND t.role IS NOT NULL AND t.password REGEXP '^\\*[0-9A-Fa-f]{40}$'
  AND t.login NOT REGEXP '^[A-Za-z0-9._-]{3,60}$';

DROP TEMPORARY TABLE tmp_users;
DROP TEMPORARY TABLE tmp_province_region;
DROP TEMPORARY TABLE tmp_activite_centre;
