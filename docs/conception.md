# Document de conception

## Architecture

Monolithe modulaire côté backend (un paquet par module), frontend Next.js séparé, Nginx devant les deux.

```
navigateur ──► Nginx ──┬── /api/*  ► Spring Boot (API REST) ──► MySQL 8
                       └── /*      ► Next.js (interface)
```

Modules backend (`ma.entraide.intranet.*`) : `security`, `territoire`, `periode`, `centre`, `statistique`, `common`.
Les migrations de schéma sont gérées par Flyway (`backend/src/main/resources/db/migration`).

## Modèle de données

```
region ─< province ─< commune
province ─< centre ─< centre_activite >─ activite >─ activite_categorie >─ axe >─ programme
centre ──1:1── centre_foncier, centre_equipement
centre_activite ─< salle
centre_activite >─ personnel (responsable)      personnel >─ province, centre (facultatif)
periode ─< saisie_province >─ province          (statut : BROUILLON, SOUMIS, VALIDE, A_CORRIGER)
periode ─< statistique >─ centre_activite       (8 indicateurs + état du centre + motif)
utilisateur (rôle ADMIN | PROVINCIAL | REGIONAL, rattaché à une province ou une région)
droit ─< role_droit                              (droits par rôle, extensible)
journal_audit
```

## Correspondance avec l'ancienne base

| Ancienne table | Nouveau modèle | Changements |
|---|---|---|
| `locaux` (56 colonnes) + `centres` | `centre`, `centre_foncier`, `centre_equipement` | Fusion locaux + centres (`nom_fr` / `nom_ar`) ; un seul couple latitude/longitude ; plus de doublons (`superficie2`, `nom2`, `addr2`...) ; booléens homogènes |
| `regions`, `provinces`, `communes` | `region`, `province`, `commune` | Clés primaires et étrangères ; noms arabes ; `INDH` devient un booléen ; indicateurs de pauvreté reportés |
| `structures` | supprimée | Remplacée par `region` et `province` (décision validée) |
| `activites` (hiérarchie en texte) | `programme` → `axe` → `activite_categorie` → `activite` | Vraie hiérarchie |
| `activite_centre` | `centre_activite` + `salle` | `salle1..salle6` deviennent des lignes ; `2222-12-31` devient `NULL` |
| `periodicites` | `periode` | Statut `OUVERTE` / `CLOTUREE` ; unicité (année, mois) |
| `statistiques` | `statistique` + `saisie_province` | Clé (activité de centre, période) ; état du centre ; statut de la saisie par province |
| `users`, `profiles`, `profiles_rights` | `utilisateur`, `droit`, `role_droit` | 3 rôles fixes + droits extensibles |

Correspondance des indicateurs : `nb_beneficiaires_m/f/alph` → `masculins`, `feminins`, `alphabetisation` ;
`nb_pers_entraide_tit/vac/vol` → `agents_titulaires/vacataires/volontaires` ; `nb_pers_part` → `agents_associations` ;
`nb_pers_prom` → `agents_autre_dep` (rapprochement à confirmer sur les données réelles) ;
`etat_centre` / `motif` → `etat_centre` / `motif`.

## Mots de passe

L'ancienne base stocke `'*' + HEX(SHA1(SHA1(mot de passe)))` (fonction MySQL `PASSWORD()`, sans sel).
À l'import, les hashs sont stockés avec le préfixe `{mysql}`. `LegacyMysqlPasswordEncoder` les vérifie, et à la première
connexion réussie le mot de passe saisi est ré-encodé en bcrypt (`{bcrypt}`). Les utilisateurs gardent leur identifiant
et leur mot de passe. Les comptes qui partagent le même hash (par exemple un mot de passe par défaut) doivent être
marqués `mot_de_passe_temporaire` à l'import pour forcer le changement.

## Périmètre d'accès

`ScopeService` : ADMIN voit tout ; REGIONAL voit les provinces de sa région ; PROVINCIAL voit sa province.
Tout accès (lecture, écriture, export) passe par ce service, jamais par un filtre côté interface seulement.

## Historique des statistiques

Une activité figure dans le tableau d'une période si elle fonctionnait pendant cette période **ou** si des chiffres existent déjà
pour elle sur cette période : l'historique repris de l'ancienne base reste donc visible même quand une activité a cessé depuis
ou que sa date d'ouverture a été reprise de façon approximative.

## Activités d'un centre, personnel, catalogue

- **Activité d'un centre** (`centre_activite`) : activité du catalogue, date d'ouverture, responsable (un agent du personnel de la même
  province), capacité d'accueil, superficie globale, gestion (Entraide Nationale, Association, Cogestion, Autre avec précision),
  association partenaire et date de la convention. Règles : « Autre » exige une précision ; une date de convention exige l'association ;
  une activité ne peut être ouverte qu'une fois par centre. Les salles (`salle1`..`salle6`) de l'ancienne base sont reprises dans `salle`
  mais ne sont pas encore modifiables dans l'interface.
- **Personnel** : table complète (identité, CIN, naissance, recrutement, fonction, adresse, situation familiale, enfants, niveau).
  Accès réservé à l'ADMIN et au PROVINCIAL de la province (droits `PERSONNEL_LIRE`, `PERSONNEL_GERER`) ; le régional n'y a pas accès.
  Pas de suppression : un agent est marqué inactif.
- **Catalogue des activités** : géré par l'ADMIN seul (droit `ACTIVITE_GERER`). Une activité retirée n'est plus proposée mais son
  historique et ses statistiques restent. La hiérarchie programme › axe › catégorie est reprise de l'ancienne base ; on choisit
  seulement la catégorie (pas encore d'écran pour créer programmes, axes et catégories).

## Interface bilingue

`I18nProvider` (cookie `lang`, lu côté serveur dans la mise en page) fournit `t()`, le sens d'écriture, le formatage des nombres et
le choix du nom (arabe si disponible). Le CSS utilise des propriétés logiques (`inset-inline-start`, `text-align: start`...) pour
fonctionner dans les deux sens. Les noms arabes viennent des colonnes `nom_ar` (région, province, commune, centre, activité).

## Exports

PDF (OpenPDF, A4 paysage, noms arabes avec la police DejaVu Sans embarquée) et Excel (Apache POI) : une section par province du
périmètre de l'utilisateur, filtre par province ou par région.

## Tests

- Backend : tests unitaires (règles de validation, hash MySQL de référence `PASSWORD('test')`) et tests d'intégration
  sur H2 en mode MySQL (authentification, périmètre, cycle de saisie complet, périodes, export, comptes).
- Frontend : tests des règles de validation (Vitest).
- Les migrations Flyway ont été validées sur H2 (tests) et sur **MySQL 8.0** réel (démarrage de l'application, validation Hibernate,
  parcours de connexion). Reste à les rejouer sur MySQL 8.4 au premier `docker compose up`.
- Migration de l'ancienne base : `migration/test/run_test.sh` (MySQL requis).
