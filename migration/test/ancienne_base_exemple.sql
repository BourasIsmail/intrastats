-- Copie SYNTHÉTIQUE de l'ancienne base, reconstruite d'après les DESCRIBE fournis, avec quelques anomalies
-- volontaires (doublons, dates 0000-00-00, hash invalide, ligne de test...) pour éprouver la migration.
-- Aucune donnée réelle. Schéma créé : __OLD__

SET SESSION sql_mode = 'NO_ENGINE_SUBSTITUTION';
SET NAMES utf8mb4;

DROP DATABASE IF EXISTS __OLD__;
CREATE DATABASE __OLD__ CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE __OLD__;

CREATE TABLE regions (
  id int NOT NULL DEFAULT 0, region varchar(40) NOT NULL DEFAULT '', region_ar varchar(120) NOT NULL,
  id_coordination int NOT NULL DEFAULT 0, id_delegation_chef int NOT NULL,
  ind_pauvr decimal(5,2) NOT NULL, ind_vuln decimal(5,2) NOT NULL, ind_vol_pauvr decimal(5,2) NOT NULL, ind_sev_pauvr decimal(5,2) NOT NULL);
CREATE TABLE provinces (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY, province varchar(30), id_delegation int, id_region int,
  ind_pauvr decimal(5,2) NOT NULL, ind_vuln decimal(5,2) NOT NULL, ind_vol_pauvr decimal(5,2) NOT NULL, ind_sev_pauvr decimal(5,2) NOT NULL);
CREATE TABLE communes (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY, commune varchar(200), id_province int, INDH char(1) NOT NULL DEFAULT '',
  longitude decimal(15,8) NOT NULL, latitude decimal(15,8) NOT NULL, code_arcgis int NOT NULL,
  ind_pauvr decimal(5,2) NOT NULL, ind_vuln decimal(5,2) NOT NULL, ind_vol_pauvr decimal(5,2) NOT NULL,
  ind_sev_pauvr decimal(5,2) NOT NULL, ind_ineg_gini decimal(5,2) NOT NULL);
CREATE TABLE structures (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY, type varchar(50), nom varchar(250) NOT NULL DEFAULT '', id_local int NOT NULL DEFAULT 0,
  id_parent int, date_creation date, tel varchar(11), fax varchar(11), adresse varchar(255), id_responsable int,
  level int NOT NULL DEFAULT 0, type_service varchar(40) NOT NULL DEFAULT '');
CREATE TABLE centres (id int NOT NULL DEFAULT 0 PRIMARY KEY, type varchar(25) DEFAULT 'Centre simple', nom_ar varchar(255) NOT NULL);
CREATE TABLE locaux (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY, categorie varchar(50) NOT NULL, type varchar(25) NOT NULL, code varchar(15) NOT NULL,
  nom varchar(250) NOT NULL, date_construction date NOT NULL DEFAULT '0000-00-00', tel varchar(50) NOT NULL, fax varchar(50) NOT NULL,
  id_province int NOT NULL DEFAULT 0, addresse0 text NOT NULL, addresse varchar(255) NOT NULL, id_str_superv int NOT NULL DEFAULT 0,
  id_responsable int NOT NULL DEFAULT 0, milieu varchar(15) NOT NULL DEFAULT 'urbain', superficie int NOT NULL DEFAULT 0,
  propriete varchar(50) NOT NULL, utilisation varchar(15) NOT NULL, etat varchar(15) NOT NULL,
  date_saisie datetime NOT NULL DEFAULT '0000-00-00 00:00:00', date_modif datetime NOT NULL DEFAULT '0000-00-00 00:00:00',
  loyer char(3) NOT NULL, montant_loyer int NOT NULL DEFAULT 0, id_commune int NOT NULL, addr2 varchar(255) NOT NULL,
  latitu decimal(20,8) NOT NULL, longit decimal(20,8) NOT NULL, exact tinyint(1) NOT NULL DEFAULT 0, nom2 varchar(120) NOT NULL,
  situation_fonciere varchar(60) NOT NULL, charge_locative decimal(10,2) NOT NULL, num_contrat text NOT NULL, consistance varchar(60) NOT NULL,
  date_contrat date NOT NULL, avenant varchar(60) NOT NULL, nom_proprietaire varchar(60) NOT NULL, exploitation varchar(15) NOT NULL,
  eau tinyint(1) NOT NULL, electricite tinyint(1) NOT NULL, assinissement tinyint(1) NOT NULL, telephone tinyint(1) NOT NULL,
  internet int NOT NULL DEFAULT 0, type_centre varchar(25) NOT NULL, nb_pc int NOT NULL DEFAULT 0, nb_imprimantes int NOT NULL DEFAULT 0,
  nb_pers_sans_form int NOT NULL DEFAULT 0, nb_pers_avec_form int NOT NULL, observation mediumtext NOT NULL, id_responsable2 int NOT NULL,
  superficie2 int NOT NULL, capacite2 int NOT NULL, fcs int NOT NULL DEFAULT 0, typecom varchar(50) NOT NULL, loi varchar(50) NOT NULL,
  loia varchar(50) NOT NULL, longitude decimal(20,8) NOT NULL, latitude decimal(20,8) NOT NULL);
CREATE TABLE activites (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY, activite varchar(125) NOT NULL DEFAULT '', description varchar(255) NOT NULL DEFAULT '',
  id_service int NOT NULL DEFAULT 0, sous_categorie varchar(150), type_activite varchar(20) NOT NULL DEFAULT 'principale',
  methode_calcul_benef varchar(25) NOT NULL DEFAULT 'moyenne', programme varchar(100) NOT NULL, sousaxe varchar(100) NOT NULL,
  axe varchar(100) NOT NULL, categorie varchar(30) NOT NULL, categorie2 varchar(100) NOT NULL, abrev varchar(10) NOT NULL, CAT varchar(12) NOT NULL,
  personnes_cibles varchar(40) NOT NULL, cat_programme varchar(50) NOT NULL, priorite int NOT NULL, axeaux varchar(40) NOT NULL,
  categorie2aux varchar(150) NOT NULL, cat_programmeaux varchar(25) NOT NULL);
CREATE TABLE activite_centre (
  Id int NOT NULL AUTO_INCREMENT PRIMARY KEY, id_centre int NOT NULL DEFAULT 0, id_responsable int, capacite int, id_activite int NOT NULL DEFAULT 0,
  date_ouvert_act datetime NOT NULL, etat varchar(7) NOT NULL DEFAULT 'Ouvert', date_cessation date NOT NULL DEFAULT '2222-12-31',
  motif varchar(255) NOT NULL, superficie int NOT NULL DEFAULT 0, nb_salles int NOT NULL DEFAULT 0,
  salle1 int NOT NULL DEFAULT 0, salle2 int NOT NULL DEFAULT 0, salle3 int NOT NULL DEFAULT 0, salle4 int NOT NULL DEFAULT 0,
  salle5 int NOT NULL DEFAULT 0, salle6 int NOT NULL DEFAULT 0, cours char(3) NOT NULL, superficie_cours int NOT NULL DEFAULT 0,
  nb_sanitaires int NOT NULL DEFAULT 0, sanitaires_convenables char(3) NOT NULL, nb_lavabos int NOT NULL DEFAULT 0, lavabos_convenables char(3) NOT NULL,
  nature_construction varchar(40) NOT NULL, aeration varchar(15) NOT NULL, gestion varchar(20) NOT NULL, gestion_autre varchar(200) NOT NULL,
  association varchar(200) NOT NULL, association2 varchar(200) NOT NULL, date_conv date NOT NULL DEFAULT '0000-00-00',
  type_gestion varchar(20) NOT NULL, type_partenaire varchar(20) NOT NULL, date_saisie date NOT NULL DEFAULT '0000-00-00');
CREATE TABLE periodicites (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY, mois int NOT NULL DEFAULT 0, annee int NOT NULL DEFAULT 0, annee_stat varchar(4) NOT NULL,
  etat tinyint(1) NOT NULL DEFAULT 0, pour_stat tinyint(1) NOT NULL DEFAULT 1);
CREATE TABLE statistiques (
  id_activite int NOT NULL, id_centre int NOT NULL, id_periodicite int NOT NULL,
  nb_beneficiaires_m int NOT NULL, nb_beneficiaires_f int NOT NULL, nb_beneficiaires_alph int NOT NULL,
  nb_pers_entraide_tit int NOT NULL, nb_pers_entraide_vac int NOT NULL, nb_pers_entraide_vol int NOT NULL,
  nb_pers_part int NOT NULL, nb_pers_prom int NOT NULL DEFAULT 0, date_saisie datetime NOT NULL, etat_centre varchar(30) NOT NULL DEFAULT 'Actif',
  motif text NOT NULL, test tinyint(1) NOT NULL DEFAULT 0, PRIMARY KEY (id_activite, id_centre, id_periodicite));
CREATE TABLE users (
  id int NOT NULL AUTO_INCREMENT PRIMARY KEY, matricule varchar(8), nom varchar(50), login varchar(20) NOT NULL DEFAULT '',
  password varchar(80) NOT NULL DEFAULT '', passwd2 varchar(80) NOT NULL, type varchar(25) NOT NULL DEFAULT '', state char(1) NOT NULL DEFAULT '1',
  id_structure int, e_mail varchar(30), heritate_rights char(1) NOT NULL DEFAULT '', default_theme varchar(120) NOT NULL DEFAULT 'Bleu gris',
  elapsed_time int NOT NULL DEFAULT 0, id_profile int NOT NULL DEFAULT 0, id_session varchar(55) NOT NULL,
  is_password_temporary tinyint(1) NOT NULL DEFAULT 1, last_connexion_at datetime);

-- ---------------------------------------------------------------- territoire
INSERT INTO regions (id, region, region_ar, id_coordination) VALUES
  (1, 'Rabat-Salé-Kénitra', 'الرباط سلا القنيطرة', 900),
  (1, 'Rabat-Salé-Kénitra (doublon)', '', 900),          -- doublon d'identifiant : une seule région doit rester
  (2, 'Fès-Meknès', 'فاس مكناس', 901);
INSERT INTO provinces (id, province, id_delegation, id_region) VALUES
  (1, 'Rabat', 100, 1), (2, 'Salé', 101, 1), (3, 'Kénitra', 102, 1), (4, 'Fès', 103, 2), (5, 'Orpheline', 104, 99);  -- 5 : région inconnue
INSERT INTO communes (id, commune, id_province, INDH, longitude, latitude, code_arcgis) VALUES
  (1, 'Rabat', 1, 'N', -6.84160000, 34.02090000, 1001),
  (2, 'Salé', 2, 'N', -6.79810000, 34.05310000, 1002),
  (3, 'Bouknadel', 2, 'O', -6.77000000, 34.12000000, 1002),    -- code ArcGIS en double
  (4, 'Kénitra', 3, 'N', 0, 0, 1004),                            -- coordonnées à 0
  (5, 'Mehdia', 3, 'O', 999.00000000, 34.25000000, 1005);        -- longitude hors bornes

-- ---------------------------------------------------------------- centres
INSERT INTO locaux (id, categorie, type, code, nom, date_construction, tel, id_province, milieu, id_commune, latitu, longit, exact,
                    propriete, situation_fonciere, montant_loyer, charge_locative, num_contrat, date_contrat, nom_proprietaire, avenant,
                    eau, electricite, assinissement, telephone, internet, nb_pc, nb_imprimantes, latitude, longitude, addresse, addresse0,
                    date_saisie, date_modif, type_centre) VALUES
  (1, 'Centre', 'Centre simple', 'RB-001', 'Centre Hassan', '1998-05-14', '0537000000', 1, 'urbain', 1, 0, 0, 1,
     'Location', 'Titrée', 2500, 150.50, 'C-2019-114', '2019-03-01', 'Propriétaire A', '', 1, 1, 1, 0, 1, 8, 2, 34.0209, -6.8416, 'Av. Hassan II', '',
     '2015-01-02 10:00:00', '0000-00-00 00:00:00', 'Centre simple'),
  (2, 'Centre', 'Centre simple', 'SL-004', 'Centre Tabriquet', '0000-00-00', '', 2, 'urbain', 2, 34.05, -6.80, 0,
     '', '', 0, 0, '', '0000-00-00', '', '', 1, 1, 0, 0, 0, 3, 1, 0, 0, '', 'Ancienne adresse', '0000-00-00 00:00:00', '0000-00-00 00:00:00', ''),
  (3, 'Centre', 'Centre simple', 'SL-004', 'Centre Hay Salam', '2012-09-01', '', 2, 'urbain', 2, 0, 0, 0,        -- code en double
     'Propriété', '', 0, 0, '', '0000-00-00', '', '', 1, 1, 1, 1, 1, 5, 1, 0, 0, 'Hay Salam', '', '2016-05-05 00:00:00', '2020-01-01 00:00:00', ''),
  (4, 'Centre', 'Centre simple', '', 'Centre Bouknadel', '2005-01-01', '', 2, 'rural', 3, 0, 0, 0,                -- code vide
     '', '', 0, 0, '', '0000-00-00', '', '', 0, 1, 0, 0, 0, 0, 0, 0, 0, '', '', '0000-00-00 00:00:00', '0000-00-00 00:00:00', ''),
  (5, 'Centre', 'Centre simple', 'XX-001', 'Centre sans province', '2005-01-01', '', 99, 'rural', 0, 0, 0, 0,        -- province inconnue
     '', '', 0, 0, '', '0000-00-00', '', '', 0, 0, 0, 0, 0, 0, 0, 0, 0, '', '', '0000-00-00 00:00:00', '0000-00-00 00:00:00', ''),
  (6, 'Siège', 'Siège', 'DEL-01', 'Délégation de Rabat', '1990-01-01', '', 1, 'urbain', 1, 0, 0, 0,                  -- local qui n'est pas un centre
     '', '', 0, 0, '', '0000-00-00', '', '', 0, 0, 0, 0, 0, 0, 0, 0, 0, '', '', '0000-00-00 00:00:00', '0000-00-00 00:00:00', '');
INSERT INTO centres (id, type, nom_ar) VALUES
  (1, 'Centre simple', 'مركز الحسن'), (2, 'Centre simple', 'مركز تابريكت'), (3, 'Centre multiservices', 'مركز حي السلام'),
  (4, 'Centre simple', 'مركز بوقنادل'), (5, 'Centre simple', 'مركز بلا إقليم');

-- ---------------------------------------------------------------- activités
INSERT INTO activites (id, activite, abrev, programme, axe, categorie2, personnes_cibles, priorite, type_activite) VALUES
  (1, 'Alphabétisation', 'ALPHA', 'Éducation et formation', 'Cohésion sociale', 'Alphabétisation et éducation', 'Adultes', 1, 'principale'),
  (2, 'Informatique de base', 'INFO', 'Éducation et formation', 'Insertion', 'Formation professionnelle', 'Jeunes', 2, 'principale'),
  (3, 'Éducation préscolaire', 'PRESC', 'Éducation et formation', 'Cohésion sociale', 'Alphabétisation et éducation', 'Enfants', 1, 'principale'),
  (4, 'Couture et broderie', 'COUT', 'Éducation et formation', 'Insertion', 'Formation professionnelle', 'Femmes', 3, 'secondaire'),
  (5, 'Activité sans classement', '', '', '', '', '', 9, 'principale');
INSERT INTO activite_centre (id_centre, id_activite, capacite, date_ouvert_act, etat, date_cessation, motif, salle1, salle2, nb_salles,
                             gestion, association, association2) VALUES
  (1, 1, 60, '2019-10-01 00:00:00', 'Ouvert', '2222-12-31', '', 30, 25, 2, 'Directe', '', ''),
  (2, 1, 60, '2019-10-01 00:00:00', 'Ouvert', '2222-12-31', '', 40, 0, 1, 'Directe', '', ''),
  (2, 2, 20, '2021-02-03 00:00:00', 'Ouvert', '2222-12-31', '', 20, 0, 1, 'Partenaire', 'Association Espoir', ''),
  (3, 3, 30, '2012-09-01 00:00:00', 'Ouvert', '2222-12-31', '', 0, 0, 0, 'Directe', '', ''),
  (3, 4, 25, '2018-09-15 00:00:00', 'Fermé', '2026-10-31', 'Travaux', 0, 0, 0, 'Directe', '', ''),
  (3, 4, 25, '0000-00-00 00:00:00', 'Ouvert', '2222-12-31', '', 0, 0, 0, 'Directe', '', ''),   -- doublon centre + activité
  (4, 1, 50, '0000-00-00 00:00:00', 'Ouvert', '2222-12-31', '', 0, 0, 0, 'Directe', '', ''),   -- date d'ouverture invalide
  (77, 1, 10, '2020-01-01 00:00:00', 'Ouvert', '2222-12-31', '', 0, 0, 0, '', '', ''),         -- centre inexistant
  (1, 88, 10, '2020-01-01 00:00:00', 'Ouvert', '2222-12-31', '', 0, 0, 0, '', '', '');         -- activité inexistante

-- ---------------------------------------------------------------- périodes et statistiques
INSERT INTO periodicites (id, mois, annee, annee_stat, etat, pour_stat) VALUES
  (1, 8, 2026, '2026', 0, 0), (2, 9, 2026, '2026', 0, 1), (3, 10, 2026, '2026', 1, 1), (4, 10, 2026, '', 1, 1), (5, 13, 2026, '2026', 0, 1);
INSERT INTO statistiques (id_activite, id_centre, id_periodicite, nb_beneficiaires_m, nb_beneficiaires_f, nb_beneficiaires_alph,
                          nb_pers_entraide_tit, nb_pers_entraide_vac, nb_pers_entraide_vol, nb_pers_part, nb_pers_prom,
                          date_saisie, etat_centre, motif, test) VALUES
  (1, 2, 2, 30, 30, 55, 2, 1, 3, 1, 0, '2026-10-02 09:00:00', 'Actif', '', 0),
  (2, 2, 2, 13, 11, 0, 1, 0, 0, 0, 0, '2026-10-02 09:00:00', 'Actif', '', 0),
  (3, 3, 2, 20, 19, 0, 2, 1, 2, 0, 0, '2026-10-02 09:00:00', 'Actif', '', 0),
  (4, 3, 2, 0, 21, 0, 1, 1, 0, 1, 0, '2026-10-02 09:00:00', 'Fermé temporairement', 'Travaux', 0),
  (1, 4, 2, 21, 40, 28, 1, 0, 1, 1, 0, '2026-10-02 09:00:00', 'Actif', '', 0),
  (1, 1, 2, 10, 15, 5, 1, 0, 0, 0, 0, '2026-10-02 09:00:00', 'Actif', '', 1),             -- ligne de test : ignorée
  (1, 2, 3, 18, 55, 40, 2, 1, 3, 1, 0, '2026-11-02 09:00:00', 'Actif', '', 0),             -- période 3 (octobre)
  (1, 2, 4, 99, 99, 0, 0, 0, 0, 0, 0, '2026-11-01 09:00:00', 'Actif', '', 0),              -- même mois via la période en double : ligne la plus ancienne écartée
  (1, 4, 3, 120000, 5, 0, 1, 0, 0, 0, 0, '2026-11-02 09:00:00', 'Actif', '', 0),            -- valeur hors bornes (signalée)
  (9, 2, 3, 1, 1, 0, 0, 0, 0, 0, 0, '2026-11-02 09:00:00', 'Actif', '', 0),               -- activité sans ligne dans activite_centre
  (1, 2, 5, 1, 1, 0, 0, 0, 0, 0, 0, '2026-11-02 09:00:00', 'Actif', '', 0);               -- période invalide (mois 13)

-- ---------------------------------------------------------------- utilisateurs
INSERT INTO structures (id, type, nom) VALUES (900, 'coordination', 'Coordination RSK'), (100, 'delegation', 'Délégation Rabat'),
  (101, 'delegation', 'Délégation Salé'), (102, 'delegation', 'Délégation Kénitra');
INSERT INTO users (id, matricule, nom, login, password, type, state, id_structure, e_mail, last_connexion_at) VALUES
  (1, '10482', 'Administrateur', 'admin', '*C73FDAE2818CD9BD13FED162B2DE48B9088047BE', 'Administrateur', '1', 0, 'admin@exemple.ma', '2026-11-20 08:00:00'),
  (2, '20114', 'Prov Salé', 'prov.sale', '*BA553CDFABF67A4D8BFC32653D70B294758B21B6', 'Provincial', '1', 101, NULL, NULL),
  (3, '20113', 'Prov Kénitra', 'prov.kenitra', '*BA553CDFABF67A4D8BFC32653D70B294758B21B6', 'Provincial', '1', 102, NULL, NULL),
  (4, '30021', 'Supervision RSK', 'reg.rsk', '*CA1956D15A1C2A78C1D8A381114E70FA4A6DAE75', 'Régional', '1', 900, NULL, NULL),
  (5, '20115', 'Ancien compte', 'ancien', '*BA553CDFABF67A4D8BFC32653D70B294758B21B6', 'Provincial', '0', 100, NULL, NULL),   -- désactivé
  (6, '', 'Hash invalide', 'badhash', 'motdepasse-en-clair', 'Provincial', '1', 100, NULL, NULL),                            -- mot de passe non migrable
  (7, '', 'Sans périmètre', 'orphelin', '*BA553CDFABF67A4D8BFC32653D70B294758B21B6', 'Provincial', '1', 555, NULL, NULL),    -- structure inconnue
  (8, '20113', 'Matricule en double', 'doublon.mat', '*94BDCEBE19083CE2A1F959FD02F964C7AF4CFC29', 'Provincial', '1', 100, NULL, NULL),
  (9, '20116', 'Login en double', 'admin', '*94BDCEBE19083CE2A1F959FD02F964C7AF4CFC29', 'Provincial', '1', 100, NULL, NULL);
