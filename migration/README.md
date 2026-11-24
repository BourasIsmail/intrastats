# Migration de l'ancienne base

Scripts SQL qui reprennent l'ancienne base (`locaux`, `centres`, `regions`, `provinces`, `communes`, `activites`,
`activite_centre`, `periodicites`, `statistiques`, `users`) dans le nouveau schéma. Rien n'est modifié dans l'ancienne base.

## Procédure

1. **Sauvegarder** l'ancienne base (`mysqldump`) et la restaurer sur le serveur MySQL 8 de migration, par exemple sous le nom `entraide_intranet_db`.
2. **Créer le nouveau schéma vide** : démarrer l'application une fois SANS le profil `dev` (Flyway crée les tables), puis l'arrêter.
3. **Lancer la migration** :
   ```bash
   cd migration
   ./migrate.sh entraide_intranet_db entraide --reset -- -uroot -p
   ```
   `--reset` vide d'abord le nouveau schéma. Sans lui, le script refuse de tourner si le nouveau schéma contient déjà des données.
4. **Lire les contrôles** affichés à la fin :
   - comptages ancien / nouveau / écartés, tous à `OK` ;
   - totaux de bénéficiaires par période, identiques, à `OK` ;
   - anomalies par catégorie (table `migration_rapport`) ;
   - comptes sans périmètre : la liste doit être vide.
5. **Traiter le rapport** : `SELECT * FROM migration_rapport WHERE categorie = '…'` donne le détail de chaque anomalie.
6. Démarrer l'application, se connecter avec un compte migré (le mot de passe reste le même).
7. Quand tout est validé : `DROP TABLE migration_rapport;`.

## Ce que fait la migration

| Ancienne table | Reprise |
|---|---|
| `regions`, `provinces`, `communes` | Identifiants conservés. Doublons d'identifiant écartés, provinces sans région et communes sans province listées. Coordonnées à 0 ou hors bornes remplacées par `NULL`. Code ArcGIS en double non repris. |
| `locaux` + `centres` | Seuls les locaux qui ont une ligne dans `centres` sont repris (les autres sont listés). Codes vides ou en double corrigés (`C-<id>`, `<code>-<id>`). Dates `0000-00-00` deviennent `NULL`. Coordonnées : `latitude/longitude`, sinon `latitu/longit`. Foncier et équipements dans leurs tables. |
| `activites` | Hiérarchie reconstruite à partir des textes (`programme`, `axe`, `categorie2`) ; valeurs vides = « Non classé ». |
| `activite_centre` | Une ligne par (centre, activité), la plus récente. **L'état (Ouvert / Fermé) fait foi** : fermé sans date valide → date de saisie ; ouvert → pas de date de cessation ; `2222-12-31` devient `NULL`. Reprend responsable (`id_responsable` → `personnel`), superficie, gestion (Entraide Nationale, Association, Cogestion ; autre valeur → « Autre » avec le texte d'origine), association et date de convention. `salle1..6` deviennent des lignes `salle`. |
| `personnel` | Identifiants conservés. Province = celle du centre (`id_local`), sinon celle dont la structure est la délégation (`id_structure`) ; sans province ou sans nom : non migré et listé. Dates invalides → `NULL`, enfants bornés à 30. Non repris : `som`, `verif`, `verif1`, `prov`, `id_str2`, `id_activite`. |
| `periodicites` | Périodes invalides écartées, doublons (année, mois) fusionnés. **Une seule période reste ouverte** (la plus récente). |
| `statistiques` | Rattachées par (centre, activité, période). Lignes `test = 1` écartées. Valeurs hors bornes reprises mais listées. Etat du centre converti. |
| (déduit) | `saisie_province` : `VALIDE` pour les périodes clôturées (historique), `BROUILLON` pour la période ouverte. |
| `users` | Rôle et périmètre déduits de la structure de rattachement (voir ci-dessous). Mot de passe repris au format `{mysql}` : remplacé par bcrypt à la première connexion. |

### Utilisateurs : règles de déduction

- `type` contenant « admin » → **ADMIN** ;
- sinon `id_structure` = `provinces.id_delegation` d'une province → **PROVINCIAL** de cette province ;
- sinon `id_structure` = `regions.id_coordination` d'une région → **REGIONAL** de cette région ;
- sinon : compte **non migré**, listé dans le rapport (à créer à la main dans l'application).

Les comptes qui partagent le même mot de passe (par exemple un mot de passe par défaut) sont marqués « mot de passe
temporaire » : l'application leur demande de le changer.

## À confirmer sur vos données réelles

Ces points sont des hypothèses faites faute d'avoir vu les données :

1. `periodicites.etat = 1` signifie « période ouverte ». Si c'est l'inverse, changer `@etat_periode_ouverte` en haut de `01_import.sql`.
2. `nb_pers_part` = agents des associations et `nb_pers_prom` = agents autre département.
3. La valeur « admin » dans `users.type` (sinon aucun compte ADMIN n'est reconnu : en créer un dans l'application).
4. Le lien `users.id_structure` → `provinces.id_delegation` / `regions.id_coordination`.
5. Les locaux migrés sont ceux présents dans `centres` ; les autres (sièges, délégations) sont exclus.
6. `activite_centre.id_responsable` désigne un `personnel.id` (vérifié sur un extrait réel : 80 % des valeurs existent dans l'extrait de `personnel` fourni).

### Constats sur un extrait réel (tables `locaux`, `centres`, `activites`, `activite_centre`, `personnel`)

Essai fait avec un territoire synthétique (les tables `regions`, `provinces`, `communes`, `structures`, `users`, `periodicites`, `statistiques` n'avaient pas été fournies). Durée : environ 1,5 s. À refaire avec la base complète.

- `centres` contient 437 lignes sans `locaux` correspondant (ni nom, ni province) : non migrées, listées (`centre_sans_local`).
- 143 locaux ont `id_province = 0` : non migrés (`centre_sans_province`) ; avec les précédents, 243 liaisons centre-activité sont écartées (`activite_centre_orpheline`).
- `locaux.code` n'est pas un code unique : 3 243 valeurs vides, beaucoup de doublons, des valeurs comme `100000` ou `0`. Presque tous les centres reçoivent donc un code généré (`C-<id>`). Il faut décider de quoi faire de ce champ.
- Dans `activite_centre.etat`, « Fermé » apparaît sous les formes `Ferme`, `Ferm&eacute;` (tronqué en `Ferm&ea`), `Fermer` : toutes reconnues par le préfixe `ferm`.
- Quelques incohérences état / date de cessation sont corrigées et comptées dans le rapport (`activite_fermee_sans_date`, `activite_ouverte_avec_date`, `date_ouverture_inconnue`, `convention_sans_association`).

## Test

`test/run_test.sh` rejoue toute la migration sur une copie **synthétique** de l'ancienne base (schéma reconstruit d'après les
`DESCRIBE`, avec des anomalies volontaires) et vérifie 33 résultats attendus. Il crée et supprime deux bases jetables :

```bash
cd migration/test
./run_test.sh -uroot -p
```
