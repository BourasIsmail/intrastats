-- Contrôles des périodes et statistiques (avec migrate.sh --avec-statistiques).
SET NAMES utf8mb4;

SELECT 'periodes' AS element, (SELECT COUNT(*) FROM __OLD__.periodicites) AS ancien, (SELECT COUNT(*) FROM __NEW__.periode) AS nouveau,
       (SELECT COUNT(*) FROM __NEW__.migration_rapport WHERE categorie IN ('periode_invalide', 'periode_doublon')) AS ecartes,
       IF((SELECT COUNT(DISTINCT annee, mois) FROM __OLD__.periodicites WHERE mois BETWEEN 1 AND 12 AND annee >= 1990) = (SELECT COUNT(*) FROM __NEW__.periode), 'OK', 'ECART') AS verdict;

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

