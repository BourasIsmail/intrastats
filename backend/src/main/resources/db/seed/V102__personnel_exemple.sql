-- Personnel d'exemple (données fictives). Identifiants automatiques : 1 à 4.
INSERT INTO personnel (matricule, nom, prenom, cin, sexe, fonction, province_id, centre_id, telephone, nb_enfants, actif) VALUES
    ('P001', 'Benali', 'Samira', 'A100001', 'F', 'Monitrice', 2, 2, '0600000001', 2, TRUE),
    ('P002', 'Alaoui', 'Youssef', 'A100002', 'M', 'Directeur de centre', 2, 2, '0600000002', 1, TRUE),
    ('P003', 'Idrissi', 'Khadija', 'B200001', 'F', 'Monitrice', 3, 5, '0600000003', 0, TRUE),
    ('P004', 'Tazi', 'Omar', 'C300001', 'M', 'Gardien', 2, 3, NULL, 3, FALSE);

UPDATE centre_activite SET responsable_id = 1, superficie = 45, gestion = 'ENTRAIDE_NATIONALE' WHERE id = 2;
UPDATE centre_activite SET gestion = 'ASSOCIATION', partenaire = 'Association Al Amal', date_convention = '2020-01-15' WHERE id = 3;
