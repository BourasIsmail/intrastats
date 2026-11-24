-- ============================================================================================
-- Contrôles après migration : comparer l'ancienne base (__OLD__) et le nouveau schéma (__NEW__).
-- Chaque ligne affiche « OK » ou « ECART ». Les écarts attendus sont expliqués par migration_rapport.
-- ============================================================================================
SET NAMES utf8mb4;

-- 1. Comptages : (ancien) - (écartés listés dans le rapport) doit égaler (nouveau)
SELECT 'regions' AS element, (SELECT COUNT(DISTINCT id) FROM __OLD__.regions WHERE id > 0) AS ancien,
       (SELECT COUNT(*) FROM __NEW__.region) AS nouveau,
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie = 'region_doublon') AS ecartes_doublons,
       IF((SELECT COUNT(DISTINCT id) FROM __OLD__.regions WHERE id > 0) = (SELECT COUNT(*) FROM __NEW__.region), 'OK', 'ECART') AS verdict
UNION ALL
SELECT 'provinces', (SELECT COUNT(*) FROM __OLD__.provinces), (SELECT COUNT(*) FROM __NEW__.province),
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie = 'province_sans_region'),
       IF((SELECT COUNT(*) FROM __OLD__.provinces) - (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie = 'province_sans_region')
          = (SELECT COUNT(*) FROM __NEW__.province), 'OK', 'ECART')
UNION ALL
SELECT 'communes', (SELECT COUNT(*) FROM __OLD__.communes), (SELECT COUNT(*) FROM __NEW__.commune),
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie = 'commune_sans_province'),
       IF((SELECT COUNT(*) FROM __OLD__.communes) - (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie = 'commune_sans_province')
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
SELECT 'periodes', (SELECT COUNT(*) FROM __OLD__.periodicites), (SELECT COUNT(*) FROM __NEW__.periode),
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie IN ('periode_invalide', 'periode_doublon')),
       IF((SELECT COUNT(DISTINCT annee, mois) FROM __OLD__.periodicites WHERE mois BETWEEN 1 AND 12 AND annee >= 1990) = (SELECT COUNT(*) FROM __NEW__.periode), 'OK', 'ECART')
UNION ALL
SELECT 'utilisateurs', (SELECT COUNT(*) FROM __OLD__.users), (SELECT COUNT(*) FROM __NEW__.utilisateur),
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie IN ('utilisateur_non_migre', 'utilisateur_login_non_conforme')),
       IF((SELECT COUNT(*) FROM __OLD__.users) - (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie IN ('utilisateur_non_migre', 'utilisateur_login_non_conforme'))
          = (SELECT COUNT(*) FROM __NEW__.utilisateur), 'OK', 'ECART');

-- 2. Totaux de bénéficiaires par période : doivent être identiques (hors lignes listées dans le rapport)
SELECT CONCAT(p.annee, '-', LPAD(p.mois, 2, '0')) AS periode,
       a.total_ancien, n.total_nouveau,
       IF(a.total_ancien = IFNULL(n.total_nouveau, 0), 'OK', 'ECART') AS verdict
FROM (SELECT annee, mois FROM __OLD__.periodicites WHERE mois BETWEEN 1 AND 12 AND annee >= 1990 GROUP BY annee, mois) p
JOIN (SELECT pp.annee, pp.mois, SUM(s.nb_beneficiaires_m + s.nb_beneficiaires_f) AS total_ancien
      FROM (
          SELECT s.*, ROW_NUMBER() OVER (PARTITION BY s.id_centre, s.id_activite, pp2.annee, pp2.mois ORDER BY s.date_saisie DESC, s.id_periodicite DESC) AS rn
          FROM __OLD__.statistiques s JOIN __OLD__.periodicites pp2 ON pp2.id = s.id_periodicite
          WHERE s.test = 0
            AND EXISTS (SELECT 1 FROM __NEW__.centre_activite ca WHERE ca.centre_id = s.id_centre AND ca.activite_id = s.id_activite)
      ) s JOIN __OLD__.periodicites pp ON pp.id = s.id_periodicite
      WHERE s.rn = 1 GROUP BY pp.annee, pp.mois) a ON a.annee = p.annee AND a.mois = p.mois
LEFT JOIN (SELECT np.annee, np.mois, SUM(st.masculins + st.feminins) AS total_nouveau
           FROM __NEW__.statistique st JOIN __NEW__.periode np ON np.id = st.periode_id GROUP BY np.annee, np.mois) n
       ON n.annee = p.annee AND n.mois = p.mois
ORDER BY p.annee, p.mois;

-- 3. Anomalies à examiner (une ligne par catégorie)
SELECT categorie, COUNT(*) AS nombre FROM __NEW__.migration_rapport GROUP BY categorie ORDER BY categorie;

-- 4. Comptes migrés qui n'ont pas pu être rattachés à une province ou une région : doit être vide
SELECT id, login, role FROM __NEW__.utilisateur
WHERE (role = 'PROVINCIAL' AND province_id IS NULL) OR (role = 'REGIONAL' AND region_id IS NULL);
