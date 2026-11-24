-- DONNÉES D'EXEMPLE (profil dev uniquement) : territoire, centres, activités, périodes, comptes.
-- Les mots de passe sont volontairement dans l'ancien format MySQL PASSWORD() ({mysql}) pour montrer
-- la migration transparente vers bcrypt à la première connexion.
--   admin / admin2026       (ADMIN)
--   prov.sale / prov2026    (PROVINCIAL, Salé)
--   prov.kenitra / prov2026 (PROVINCIAL, Kénitra)
--   reg.rsk / reg2026       (REGIONAL, Rabat-Salé-Kénitra)

INSERT INTO region (id, nom_fr, nom_ar) VALUES
    (1, 'Rabat-Salé-Kénitra', 'الرباط سلا القنيطرة'),
    (2, 'Fès-Meknès', 'فاس مكناس');

INSERT INTO province (id, nom_fr, nom_ar, region_id) VALUES
    (1, 'Rabat', 'الرباط', 1),
    (2, 'Salé', 'سلا', 1),
    (3, 'Kénitra', 'القنيطرة', 1),
    (4, 'Fès', 'فاس', 2);

INSERT INTO commune (id, nom_fr, nom_ar, province_id, indh) VALUES
    (1, 'Rabat', 'الرباط', 1, FALSE),
    (2, 'Salé', 'سلا', 2, FALSE),
    (3, 'Bouknadel', 'بوقنادل', 2, TRUE),
    (4, 'Kénitra', 'القنيطرة', 3, FALSE),
    (5, 'Mehdia', 'المهدية', 3, TRUE);

INSERT INTO utilisateur (id, matricule, nom, login, mot_de_passe, role, region_id, province_id, actif, mot_de_passe_temporaire) VALUES
    (1, '10482', 'Administration centrale', 'admin', '{mysql}*C73FDAE2818CD9BD13FED162B2DE48B9088047BE', 'ADMIN', NULL, NULL, TRUE, FALSE),
    (2, '20114', 'Compte provincial Salé', 'prov.sale', '{mysql}*BA553CDFABF67A4D8BFC32653D70B294758B21B6', 'PROVINCIAL', NULL, 2, TRUE, FALSE),
    (3, '20113', 'Compte provincial Kénitra', 'prov.kenitra', '{mysql}*BA553CDFABF67A4D8BFC32653D70B294758B21B6', 'PROVINCIAL', NULL, 3, TRUE, TRUE),
    (4, '30021', 'Supervision Rabat-Salé-Kénitra', 'reg.rsk', '{mysql}*CA1956D15A1C2A78C1D8A381114E70FA4A6DAE75', 'REGIONAL', 1, NULL, TRUE, FALSE);

INSERT INTO centre (id, code, nom_fr, nom_ar, categorie, type, province_id, commune_id, milieu) VALUES
    (1, 'RB-001', 'Centre Hassan', 'مركز الحسن', 'Centre', 'Centre simple', 1, 1, 'urbain'),
    (2, 'SL-004', 'Centre Tabriquet', 'مركز تابريكت', 'Centre', 'Centre simple', 2, 2, 'urbain'),
    (3, 'SL-006', 'Centre Hay Salam', 'مركز حي السلام', 'Centre', 'Centre multiservices', 2, 2, 'urbain'),
    (4, 'SL-009', 'Centre Bouknadel', 'مركز بوقنادل', 'Centre', 'Centre simple', 2, 3, 'rural'),
    (5, 'KN-007', 'Centre Maamora', 'مركز المعمورة', 'Centre', 'Centre simple', 3, 4, 'urbain'),
    (6, 'KN-011', 'Centre Sidi Taibi', 'مركز سيدي الطيبي', 'Centre', 'Centre simple', 3, 4, 'rural'),
    (7, 'KN-015', 'Centre Mehdia', 'مركز المهدية', 'Centre', 'Centre simple', 3, 5, 'rural');

INSERT INTO programme (id, nom) VALUES (1, 'Éducation et formation'), (2, 'Action sociale');
INSERT INTO axe (id, nom, programme_id) VALUES (1, 'Cohésion sociale', 1), (2, 'Insertion', 1), (3, 'Protection sociale', 2);
INSERT INTO activite_categorie (id, nom, axe_id) VALUES
    (1, 'Alphabétisation et éducation', 1), (2, 'Formation professionnelle', 2), (3, 'Personnes âgées et enfance', 3);

INSERT INTO activite (id, nom, abreviation, categorie_id) VALUES
    (1, 'Alphabétisation', 'ALPHA', 1),
    (2, 'Informatique de base', 'INFO', 2),
    (3, 'Éducation préscolaire', 'PRESC', 1),
    (4, 'Couture et broderie', 'COUT', 2),
    (5, 'Espace multifonctionnel de la femme (EMF)', 'EMF', 3),
    (6, 'Centre des personnes âgées', 'CPA', 3);

-- Activités ouvertes (date_cessation NULL = sans fin)
INSERT INTO centre_activite (id, centre_id, activite_id, date_ouverture, capacite) VALUES
    (1, 1, 6, '2015-01-05', 40),
    (2, 2, 1, '2019-10-01', 60),
    (3, 2, 2, '2021-02-03', 20),
    (4, 3, 3, '2012-09-01', 30),
    (5, 3, 4, '2018-09-15', 25),
    (6, 4, 1, '2019-10-01', 50),
    (7, 5, 1, '2019-10-01', 40),
    (8, 5, 5, '2020-03-01', 35),
    (9, 6, 6, '2016-04-01', 30),
    (10, 7, 3, '2014-09-01', 25);

-- Périodes : septembre clôturée, octobre ouverte
INSERT INTO periode (id, mois, annee, annee_stat, statut, comptee_en_stats) VALUES
    (1, 9, 2026, '2026', 'CLOTUREE', TRUE),
    (2, 10, 2026, '2026', 'OUVERTE', TRUE);

-- Septembre : Salé validée (sert de base de comparaison pour octobre)
INSERT INTO saisie_province (id, province_id, periode_id, statut, soumise_le, soumise_par, decision_le, decision_par) VALUES
    (1, 2, 1, 'VALIDE', '2026-10-02 09:42:00', 2, '2026-10-03 11:00:00', 1);

INSERT INTO statistique (centre_activite_id, periode_id, masculins, feminins, alphabetisation, agents_titulaires,
                         agents_vacataires, agents_volontaires, agents_associations, agents_autre_dep, etat_centre) VALUES
    (2, 1, 30, 30, 55, 2, 1, 3, 1, 0, 'ACTIF'),
    (3, 1, 13, 11, 0, 1, 0, 0, 0, 0, 'ACTIF'),
    (4, 1, 20, 19, 0, 2, 1, 2, 0, 0, 'ACTIF'),
    (5, 1, 0, 21, 0, 1, 1, 0, 1, 0, 'ACTIF'),
    (6, 1, 21, 40, 28, 1, 0, 1, 1, 0, 'ACTIF');
