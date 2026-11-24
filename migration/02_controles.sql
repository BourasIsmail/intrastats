-- ============================================================================================
-- Contrôles après migration : comparer l'ancienne base (__OLD__) et le nouveau schéma (__NEW__).
-- Chaque ligne affiche « OK » ou « ECART ». Les écarts attendus sont expliqués par migration_rapport.
-- ============================================================================================
SET NAMES utf8mb4;

-- 1. Comptages : (ancien) - (écartés listés dans le rapport) doit égaler (nouveau)
SELECT 'regions' AS element, (SELECT COUNT(DISTINCT id) FROM __OLD__.regions_nv WHERE id > 0) AS ancien,
       (SELECT COUNT(*) FROM __NEW__.region) AS nouveau,
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie = 'region_doublon') AS ecartes_doublons,
       IF((SELECT COUNT(DISTINCT id) FROM __OLD__.regions_nv WHERE id > 0) = (SELECT COUNT(*) FROM __NEW__.region), 'OK', 'ECART') AS verdict
UNION ALL
SELECT 'provinces', (SELECT COUNT(*) FROM __OLD__.provinces), (SELECT COUNT(*) FROM __NEW__.province),
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie = 'province_sans_region'),
       IF((SELECT COUNT(*) FROM __OLD__.provinces) - (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie = 'province_sans_region')
          = (SELECT COUNT(*) FROM __NEW__.province), 'OK', 'ECART')
UNION ALL
SELECT 'communes', (SELECT COUNT(*) FROM __OLD__.communes), (SELECT COUNT(*) FROM __NEW__.commune),
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie IN ('commune_sans_province', 'commune_sans_nom')),
       IF((SELECT COUNT(*) FROM __OLD__.communes) - (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie IN ('commune_sans_province', 'commune_sans_nom'))
          = (SELECT COUNT(*) FROM __NEW__.commune), 'OK', 'ECART')
UNION ALL
SELECT 'centres', (SELECT COUNT(*) FROM __OLD__.centres), (SELECT COUNT(*) FROM __NEW__.centre),
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie = 'centre_sans_province'),
       IF((SELECT COUNT(*) FROM __OLD__.centres c JOIN __OLD__.locaux l ON l.id = c.id) - (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie = 'centre_sans_province')
          = (SELECT COUNT(*) FROM __NEW__.centre), 'OK', 'ECART')
UNION ALL
SELECT 'activites', (SELECT COUNT(*) FROM __OLD__.activites), (SELECT COUNT(*) FROM __NEW__.activite), 0,
       IF((SELECT COUNT(*) FROM __OLD__.activites WHERE TRIM(activite) <> '') = (SELECT COUNT(*) FROM __NEW__.activite), 'OK', 'ECART')
UNION ALL
SELECT 'activites par centre', (SELECT COUNT(DISTINCT id_centre, id_activite) FROM __OLD__.activite_centre),
       (SELECT COUNT(*) FROM __NEW__.centre_activite),
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie = 'activite_centre_orpheline'),
       IF((SELECT COUNT(*) FROM (SELECT 1 FROM __OLD__.activite_centre ac
                                 WHERE EXISTS (SELECT 1 FROM __NEW__.centre c WHERE c.id = ac.id_centre)
                                   AND EXISTS (SELECT 1 FROM __NEW__.activite a WHERE a.id = ac.id_activite)
                                 GROUP BY ac.id_centre, ac.id_activite) q) = (SELECT COUNT(*) FROM __NEW__.centre_activite), 'OK', 'ECART')
UNION ALL
SELECT 'utilisateurs', (SELECT COUNT(*) FROM __OLD__.users), (SELECT COUNT(*) FROM __NEW__.utilisateur),
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie IN ('utilisateur_non_migre', 'utilisateur_login_non_conforme')),
       IF((SELECT COUNT(*) FROM __OLD__.users) - (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie IN ('utilisateur_non_migre', 'utilisateur_login_non_conforme'))
          = (SELECT COUNT(*) FROM __NEW__.utilisateur), 'OK', 'ECART');

-- 2. Anomalies à examiner (une ligne par catégorie)
SELECT categorie, COUNT(*) AS nombre FROM __NEW__.migration_rapport GROUP BY categorie ORDER BY categorie;

-- 3. Comptes migrés qui n'ont pas pu être rattachés à une province ou une région : doit être vide
SELECT id, login, role FROM __NEW__.utilisateur
WHERE (role = 'PROVINCIAL' AND province_id IS NULL) OR (role = 'REGIONAL' AND region_id IS NULL);
