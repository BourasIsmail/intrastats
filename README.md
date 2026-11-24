# Entraide Intranet : refonte

Intranet de gestion des centres, des activités et des statistiques mensuelles.
Cette version remplace l'ancienne application (PHP/MySQL) avec un modèle de données assaini.

| Couche | Technologie |
|---|---|
| Backend | Java 21, Spring Boot 3.4, Spring Data JPA, Spring Security (JWT en cookie HttpOnly), Flyway |
| Frontend | Next.js 15 (App Router), React 19, TypeScript |
| Base de données | MySQL 8.4 |
| Déploiement | Docker + Docker Compose, Nginx en reverse proxy |

## Démarrage rapide (Docker)

```bash
cp .env.example .env        # puis éditez les mots de passe et APP_JWT_SECRET
docker compose up --build
```

L'application est servie sur http://localhost:8080 (port réglable avec `HTTP_PORT`).

Pour essayer avec des données d'exemple (jamais en production), mettez `SPRING_PROFILES_ACTIVE=dev` dans `.env` :

| Compte | Mot de passe | Rôle |
|---|---|---|
| `admin` | `admin2026` | ADMIN |
| `prov.sale` | `prov2026` | PROVINCIAL (Salé) |
| `prov.kenitra` | `prov2026` | PROVINCIAL (Kénitra) |
| `reg.rsk` | `reg2026` | REGIONAL (Rabat-Salé-Kénitra) |

Ces comptes sont dans l'**ancien format de mot de passe MySQL** exprès : à la première connexion, le hash est
remplacé par du bcrypt, sans que l'utilisateur change de mot de passe (voir `docs/conception.md`).

## Développement local

```bash
# Backend (nécessite une base MySQL ; sinon lancer les tests, qui utilisent H2)
cd backend
mvn test
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# Frontend
cd frontend
npm install
npm run dev          # http://localhost:3000, relaie /api vers http://localhost:8080
npm test             # règles de validation
```

## Rôles et droits

| Rôle | Peut faire |
|---|---|
| **ADMIN** | Créer, clôturer et rouvrir les périodes ; gérer les utilisateurs ; gérer les centres, le **personnel** et le **catalogue des activités** ; **vérifier** les saisies (valider ou renvoyer) ; tout consulter et exporter |
| **PROVINCIAL** | Gérer les centres, leurs activités et le personnel de **sa province** ; saisir et soumettre ses statistiques sur la période ouverte ; consulter ses périodes passées |
| **REGIONAL** | Consulter et exporter les statistiques et les centres des provinces de **sa région** (lecture seule ; pas d'accès au personnel : CIN, adresse, situation familiale) |

Les droits fins sont dans les tables `droit` et `role_droit` : on peut en ajouter sans toucher aux trois rôles.

## Cycle d'une saisie

`BROUILLON` → `SOUMIS` → `VALIDE`, ou `SOUMIS` → `A_CORRIGER` (renvoi par l'admin, motif obligatoire) → `SOUMIS`.
La saisie n'est possible que sur la période **ouverte**. Une période clôturée verrouille tout ; seul l'admin
peut la rouvrir (une seule période ouverte à la fois). Chaque décision est tracée dans `journal_audit`.

## Règles de validation (client et serveur)

- Huit indicateurs obligatoires à la soumission : entiers de 0 à 99 999.
- Alphabétisation ≤ masculins + féminins.
- Si l'état du centre n'est pas « Actif » : motif d'au moins 5 caractères.
- Avertissement non bloquant : centre actif sans aucun bénéficiaire.
- À la vérification, un écart d'au moins 50 % et 5 unités par rapport au mois précédent est signalé.

Le navigateur applique les mêmes règles pour un retour immédiat (`frontend/src/lib/rules.ts`) ; **le serveur fait foi**
(`backend/.../statistique/Regles.java`).

## API (extrait)

| Méthode | Chemin | Rôle |
|---|---|---|
| POST | `/api/auth/login`, `/logout` · GET `/api/auth/me` | tous |
| GET/POST | `/api/periodes` · POST `/{id}/cloturer`, `/{id}/rouvrir` | lecture : tous ; écriture : ADMIN |
| GET | `/api/regions`, `/api/provinces`, `/api/communes` | selon le périmètre |
| GET/POST/PUT | `/api/centres`, `/api/centres/{id}` (identité, localisation, foncier, équipements) · POST `/{id}/activites` · PUT `/{id}/activites/{n}` (responsable, capacité, superficie, gestion, partenaire, convention) · POST `/{id}/activites/{n}/cesser` | lecture : tous ; écriture : ADMIN, PROVINCIAL (sa province) |
| GET | `/api/activites` (liste de choix, activités actives) | tous |
| GET/POST/PUT | `/api/personnel` (filtres `centreId`, `provinceId`, `q`, `actifSeulement`) | ADMIN, PROVINCIAL (sa province) |
| GET/POST/PUT | `/api/admin/activites` (catalogue) · GET `/categories` | ADMIN |
| GET | `/api/statistiques?periodeId&provinceId` | tableau (saisie, vérification ou consultation) |
| PUT | `/api/statistiques/lignes` · POST `/soumettre`, `/brouillon` | PROVINCIAL |
| POST | `/api/statistiques/valider`, `/renvoyer` | ADMIN |
| GET | `/api/saisies` · `/api/tableau-de-bord` | selon le périmètre |
| GET | `/api/statistiques/export.pdf` · `/export.xlsx` (`periodeId`, option `regionId` ou `provinceId`) | selon le périmètre : une section par province |
| GET/POST/PUT | `/api/utilisateurs` · POST `/{id}/reinitialiser` | ADMIN |

La documentation interactive (Swagger) est disponible sur `/swagger-ui.html` avec le profil `dev` uniquement.

## Sécurité

- Jeton JWT dans un cookie `HttpOnly`, `SameSite=Strict`, `Secure` (réglé par `APP_COOKIE_SECURE`).
- Toute requête qui modifie des données doit porter l'en-tête `X-Requested-With` (défense CSRF supplémentaire).
- Mots de passe : bcrypt ; ancien format MySQL accepté uniquement en lecture, remplacé à la première connexion.
- 5 échecs de connexion en 15 minutes bloquent l'identifiant (en mémoire, instance unique).
- Le périmètre géographique est contrôlé côté serveur à chaque lecture et écriture.
- `APP_JWT_SECRET` est obligatoire (32 caractères minimum) et n'a aucune valeur par défaut en production.

## Migration de l'ancienne base

Les scripts et la procédure sont dans [`migration/`](migration/README.md) : import avec contrôles (comptages, totaux de
bénéficiaires par période), rapport d'anomalies et test automatisé sur une copie synthétique de l'ancienne base.

## Langues : français et arabe (RTL)

L'interface est bilingue. Le bouton de langue est dans le menu (et sur la page de connexion) ; le choix est mémorisé dans un
cookie et lu côté serveur, donc la page arrive déjà dans le bon sens (droite à gauche en arabe).

- Les textes sont dans `frontend/src/lib/i18n/fr.ts` (référence) et `ar.ts`. Un test vérifie que les deux fichiers ont exactement
  les mêmes clés et les mêmes paramètres, et qu'aucun texte arabe n'est resté en français.
- Les noms arabes des régions, provinces, communes, centres et activités s'affichent en arabe quand ils existent (sinon le français).
- Les chiffres restent latins (0-9), comme dans l'administration marocaine.
- **Les traductions arabes doivent être relues par un arabophone de l'institution**, surtout les termes métier (statuts, catégories
  de personnel). Les corrections se font dans `ar.ts` uniquement.
- Les messages d'erreur renvoyés par le serveur sont en français : en arabe, l'interface affiche un message général traduit et
  garde le message d'origine entre parenthèses.
- Les exports PDF et Excel restent en français (avec les noms arabes des centres en PDF).

## Reste à faire

- Indicateurs de pauvreté des régions, provinces et communes (module `territoire_indicateur`), mis de côté à votre demande.
- Gestion des droits fins depuis l'interface (aujourd'hui dans les tables `droit` et `role_droit`).
- Messages d'erreur du serveur et exports PDF / Excel en arabe.
- TLS : à terminer devant Nginx (ou un reverse proxy d'entreprise), puis `APP_COOKIE_SECURE=true`.
- Premier `docker compose up` sur une machine avec Docker : les images n'ont pas pu être construites dans l'environnement de développement
  (les migrations, elles, ont été validées sur MySQL 8.0).
