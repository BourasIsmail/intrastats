-- ============================================================================================
-- OPTIONNEL : périodes, statistiques et historique des saisies (lancer avec migrate.sh --avec-statistiques).
-- Non exécuté par défaut : la nouvelle application démarre sans historique de statistiques.
-- Même substitution __OLD__/__NEW__ que 01_import.sql ; à exécuter après lui (utilise les centres et activités migrés).
-- ============================================================================================
SET SESSION sql_mode = 'NO_ENGINE_SUBSTITUTION';
SET NAMES utf8mb4;
USE __NEW__;

-- Hypothèse à confirmer : periodicites.etat = 1 signifie « période ouverte » (0 = clôturée).
SET @etat_periode_ouverte = 1;

-- --------------------------------------------------------------------------------------------
-- 5. Périodes et statistiques
-- --------------------------------------------------------------------------------------------
INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'periode_invalide', id, CONCAT('mois ', mois, ' / année ', annee, ' : période invalide, écartée')
FROM __OLD__.periodicites WHERE mois NOT BETWEEN 1 AND 12 OR annee < 1990;

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'periode_doublon', MIN(id), CONCAT(mois, '/', annee, ' : ', COUNT(*), ' périodes, les statistiques sont rattachées à la première')
FROM __OLD__.periodicites WHERE mois BETWEEN 1 AND 12 AND annee >= 1990 GROUP BY annee, mois HAVING COUNT(*) > 1;

INSERT INTO __NEW__.periode (id, mois, annee, annee_stat, statut, comptee_en_stats)
SELECT MIN(p.id), p.mois, p.annee, LEFT(COALESCE(NULLIF(MAX(p.annee_stat), ''), CAST(p.annee AS CHAR)), 4),
       CASE WHEN MAX(p.etat) = @etat_periode_ouverte THEN 'OUVERTE' ELSE 'CLOTUREE' END,
       MAX(p.pour_stat) <> 0
FROM __OLD__.periodicites p
WHERE p.mois BETWEEN 1 AND 12 AND p.annee >= 1990
GROUP BY p.annee, p.mois;

-- Une seule période peut être ouverte : si plusieurs le sont, seule la plus récente le reste.
UPDATE __NEW__.periode SET statut = 'CLOTUREE'
WHERE statut = 'OUVERTE'
  AND id <> (SELECT m.id FROM (SELECT id FROM __NEW__.periode WHERE statut = 'OUVERTE' ORDER BY annee DESC, mois DESC LIMIT 1) m);

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'statistique_test', COUNT(*), 'lignes marquées test=1 : non migrées' FROM __OLD__.statistiques WHERE test <> 0 HAVING COUNT(*) > 0;

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'statistique_orpheline', CONCAT(s.id_centre, '/', s.id_activite, '/', s.id_periodicite),
       'centre/activité ou période introuvable dans le nouveau schéma : ligne écartée'
FROM __OLD__.statistiques s
LEFT JOIN __OLD__.periodicites p ON p.id = s.id_periodicite
LEFT JOIN __NEW__.periode np ON np.annee = p.annee AND np.mois = p.mois
LEFT JOIN __NEW__.centre_activite ca ON ca.centre_id = s.id_centre AND ca.activite_id = s.id_activite
WHERE s.test = 0 AND (np.id IS NULL OR ca.id IS NULL);

INSERT INTO __NEW__.statistique (centre_activite_id, periode_id, masculins, feminins, alphabetisation, agents_titulaires,
                                 agents_vacataires, agents_volontaires, agents_associations, agents_autre_dep,
                                 etat_centre, motif, updated_at)
SELECT x.ca_id, x.periode_id, x.nb_beneficiaires_m, x.nb_beneficiaires_f, x.nb_beneficiaires_alph, x.nb_pers_entraide_tit,
       x.nb_pers_entraide_vac, x.nb_pers_entraide_vol, x.nb_pers_part, x.nb_pers_prom,
       CASE WHEN LOWER(x.etat_centre) LIKE 'actif%' OR TRIM(x.etat_centre) = '' THEN 'ACTIF'
            WHEN LOWER(x.etat_centre) LIKE '%définitiv%' OR LOWER(x.etat_centre) LIKE '%definitiv%' THEN 'FERME_DEFINITIVEMENT'
            ELSE 'FERME_TEMPORAIREMENT' END,
       LEFT(NULLIF(TRIM(x.motif), ''), 500),
       CASE WHEN x.date_saisie >= '1990-01-01' THEN x.date_saisie ELSE NOW() END
FROM (
    SELECT s.*, ca.id AS ca_id, np.id AS periode_id,
           ROW_NUMBER() OVER (PARTITION BY ca.id, np.id ORDER BY s.date_saisie DESC, s.id_periodicite DESC) AS rn
    FROM __OLD__.statistiques s
    JOIN __OLD__.periodicites p ON p.id = s.id_periodicite
    JOIN __NEW__.periode np ON np.annee = p.annee AND np.mois = p.mois
    JOIN __NEW__.centre_activite ca ON ca.centre_id = s.id_centre AND ca.activite_id = s.id_activite
    WHERE s.test = 0
) x
WHERE x.rn = 1;

INSERT INTO __NEW__.migration_rapport (categorie, ancien_id, detail)
SELECT 'statistique_hors_bornes', st.id, 'valeur négative ou supérieure à 99 999 : reprise telle quelle, à vérifier'
FROM __NEW__.statistique st
WHERE LEAST(IFNULL(masculins,0), IFNULL(feminins,0), IFNULL(alphabetisation,0), IFNULL(agents_titulaires,0), IFNULL(agents_vacataires,0),
            IFNULL(agents_volontaires,0), IFNULL(agents_associations,0), IFNULL(agents_autre_dep,0)) < 0
   OR GREATEST(IFNULL(masculins,0), IFNULL(feminins,0), IFNULL(alphabetisation,0), IFNULL(agents_titulaires,0), IFNULL(agents_vacataires,0),
            IFNULL(agents_volontaires,0), IFNULL(agents_associations,0), IFNULL(agents_autre_dep,0)) > 99999;

-- Etat de la saisie par province : les périodes clôturées sont historiques donc VALIDE ; la période ouverte reste en BROUILLON.
INSERT INTO __NEW__.saisie_province (province_id, periode_id, statut)
SELECT DISTINCT ce.province_id, st.periode_id, CASE WHEN p.statut = 'CLOTUREE' THEN 'VALIDE' ELSE 'BROUILLON' END
FROM __NEW__.statistique st
JOIN __NEW__.centre_activite ca ON ca.id = st.centre_activite_id
JOIN __NEW__.centre ce ON ce.id = ca.centre_id
JOIN __NEW__.periode p ON p.id = st.periode_id;

