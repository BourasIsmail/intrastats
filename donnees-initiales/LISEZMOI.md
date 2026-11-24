# Données initiales

Placez ici le fichier `V6_1__donnees_initiales.sql` (généré par `migration/exporter_donnees.sh`).
Au premier démarrage de l'application sur une base vide, Flyway l'exécute une seule fois, juste après la création des tables.

- Le contenu de ce dossier n'est **pas versionné** : il contient des données personnelles (CIN, adresses) et des mots de passe chiffrés.
- Pour recharger d'autres données : supprimer la base (`docker compose down -v`), remplacer le fichier, relancer.
- Sans fichier, l'application démarre avec une base vide.
