# Données initiales

Placez ici le fichier `V6_1__donnees_initiales.sql` (centres, activités, personnel, comptes) et, si vous voulez l'historique, `V6_2__statistiques_initiales.sql` (périodes et statistiques, environ 50 Mo). Ils sont générés par `migration/exporter_donnees.sh` ; V6_2 suppose que V6_1 est chargé avant.
Au premier démarrage de l'application sur une base vide, Flyway l'exécute une seule fois, juste après la création des tables.

- Le contenu de ce dossier n'est **pas versionné** : il contient des données personnelles (CIN, adresses) et des mots de passe chiffrés.
- Pour recharger d'autres données : supprimer la base (`docker compose down -v`), remplacer le fichier, relancer.
- Sans fichier, l'application démarre avec une base vide.
