#!/usr/bin/env bash
# Test de la migration sur une copie SYNTHÉTIQUE de l'ancienne base (aucune donnée réelle).
# Crée deux bases jetables, joue la migration, puis vérifie des résultats attendus.
#
# Usage :  ./run_test.sh [options du client mysql]      (ex. : ./run_test.sh -uroot -p)
# Attention : supprime et recrée les bases « entraide_old_test » et « entraide_new_test ».
set -euo pipefail

DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$DIR/../.." && pwd)"
MYSQL=(mysql --default-character-set=utf8mb4 "$@")
OLD=entraide_old_test
NEW=entraide_new_test

echo "→ Ancienne base synthétique ($OLD)"
sed "s/__OLD__/$OLD/g" "$DIR/ancienne_base_exemple.sql" | "${MYSQL[@]}"

echo "→ Nouveau schéma vide ($NEW), migrations Flyway V1 à V4"
"${MYSQL[@]}" -e "DROP DATABASE IF EXISTS $NEW; CREATE DATABASE $NEW CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
for f in "$ROOT"/backend/src/main/resources/db/migration/V*.sql; do "${MYSQL[@]}" "$NEW" < "$f"; done

echo "→ Migration"
"$DIR/../migrate.sh" "$OLD" "$NEW" -- "$@" > /tmp/migration-test.log 2>&1 || { cat /tmp/migration-test.log; exit 1; }

echo "→ Vérifications"
fail=0
q() { "${MYSQL[@]}" -N -e "$1"; }
check() {   # check "libellé" "attendu" "requête"
  local got; got="$(q "$3")"
  if [[ "$got" == "$2" ]]; then echo "  OK    $1"; else echo "  ECHEC $1 : attendu '$2', obtenu '$got'"; fail=1; fi
}

if grep -q "ECART" /tmp/migration-test.log; then echo "  ECHEC les contrôles signalent un écart"; grep ECART /tmp/migration-test.log; fail=1; fi

check "régions (le doublon est écarté)"                   "2"  "SELECT COUNT(*) FROM $NEW.region"
check "la région conserve son nom arabe"                  "الرباط سلا القنيطرة" "SELECT nom_ar FROM $NEW.region WHERE id = 1"
check "province sans région écartée"                      "4"  "SELECT COUNT(*) FROM $NEW.province"
check "centres migrés (non-centre et province inconnue écartés)" "4" "SELECT COUNT(*) FROM $NEW.centre"
check "code en double corrigé"                            "SL-004-3" "SELECT code FROM $NEW.centre WHERE id = 3"
check "code vide remplacé"                                "C-4" "SELECT code FROM $NEW.centre WHERE id = 4"
check "date 0000-00-00 devient NULL"                      "NULL" "SELECT IFNULL(date_construction, 'NULL') FROM $NEW.centre WHERE id = 2"
check "coordonnées de repli (latitu/longit)"              "34.0500000" "SELECT latitude FROM $NEW.centre WHERE id = 2"
check "coordonnées hors bornes écartées"                  "NULL" "SELECT IFNULL(longitude, 'NULL') FROM $NEW.commune WHERE id = 5"
check "code ArcGIS en double non repris"                  "NULL" "SELECT IFNULL(code_arcgis, 'NULL') FROM $NEW.commune WHERE id = 3"
check "nom arabe du centre repris"                        "مركز الحسن" "SELECT nom_ar FROM $NEW.centre WHERE id = 1"
check "loyer et contrat repris"                           "2500.00|C-2019-114" "SELECT CONCAT(loyer_mensuel, '|', numero_contrat) FROM $NEW.centre_foncier WHERE centre_id = 1"
check "équipements repris"                                "1|1|8" "SELECT CONCAT(eau, '|', internet, '|', nb_ordinateurs) FROM $NEW.centre_equipement WHERE centre_id = 1"
check "hiérarchie d'activités"                            "3" "SELECT COUNT(*) FROM $NEW.activite_categorie"
check "activités migrées"                                 "5" "SELECT COUNT(*) FROM $NEW.activite"
check "activités par centre (doublon et orphelins écartés)" "6" "SELECT COUNT(*) FROM $NEW.centre_activite"
check "sentinelle 2222-12-31 devient NULL"                "NULL" "SELECT IFNULL(date_cessation, 'NULL') FROM $NEW.centre_activite WHERE centre_id = 1 AND activite_id = 1"
check "vraie date de cessation conservée"                 "2026-10-31" "SELECT date_cessation FROM $NEW.centre_activite WHERE centre_id = 3 AND activite_id = 4"
check "salles reprises (salle1, salle2)"                  "2" "SELECT COUNT(*) FROM $NEW.salle s JOIN $NEW.centre_activite ca ON ca.id = s.centre_activite_id WHERE ca.centre_id = 1"
check "périodes (invalide et doublon écartés)"            "3" "SELECT COUNT(*) FROM $NEW.periode"
check "une seule période ouverte"                         "1" "SELECT COUNT(*) FROM $NEW.periode WHERE statut = 'OUVERTE'"
check "ligne de test non migrée"                          "0" "SELECT COUNT(*) FROM $NEW.statistique st JOIN $NEW.centre_activite ca ON ca.id = st.centre_activite_id WHERE ca.centre_id = 1 AND ca.activite_id = 1"
check "total septembre identique (hors ligne de test)"    "205" "SELECT SUM(masculins + feminins) FROM $NEW.statistique st JOIN $NEW.periode p ON p.id = st.periode_id WHERE p.mois = 9"
check "doublon de période : la saisie la plus récente gagne" "73" "SELECT masculins + feminins FROM $NEW.statistique st JOIN $NEW.centre_activite ca ON ca.id = st.centre_activite_id JOIN $NEW.periode p ON p.id = st.periode_id WHERE ca.centre_id = 2 AND ca.activite_id = 1 AND p.mois = 10"
check "état « fermé » converti"                           "FERME_TEMPORAIREMENT" "SELECT etat_centre FROM $NEW.statistique st JOIN $NEW.centre_activite ca ON ca.id = st.centre_activite_id WHERE ca.centre_id = 3 AND ca.activite_id = 4"
check "saisie historique validée, période ouverte en brouillon" "BROUILLON,VALIDE" "SELECT GROUP_CONCAT(statut ORDER BY statut) FROM $NEW.saisie_province"
check "utilisateurs migrés"                               "6" "SELECT COUNT(*) FROM $NEW.utilisateur"
check "rôle provincial et province déduits de la structure" "PROVINCIAL|2" "SELECT CONCAT(role, '|', province_id) FROM $NEW.utilisateur WHERE login = 'prov.sale'"
check "rôle régional et région déduits"                   "REGIONAL|1" "SELECT CONCAT(role, '|', region_id) FROM $NEW.utilisateur WHERE login = 'reg.rsk'"
check "mot de passe repris au format {mysql}"             "{mysql}*C73FDAE2818CD9BD13FED162B2DE48B9088047BE" "SELECT mot_de_passe FROM $NEW.utilisateur WHERE login = 'admin'"
check "mot de passe partagé : changement forcé"           "1" "SELECT mot_de_passe_temporaire FROM $NEW.utilisateur WHERE login = 'prov.sale'"
check "compte désactivé reste désactivé"                  "0" "SELECT actif FROM $NEW.utilisateur WHERE login = 'ancien'"
check "comptes non migrables listés dans le rapport"      "3" "SELECT COUNT(*) FROM $NEW.migration_rapport WHERE categorie = 'utilisateur_non_migre'"

"${MYSQL[@]}" -e "DROP DATABASE $OLD; DROP DATABASE $NEW;"
if [[ $fail -eq 0 ]]; then echo "→ Tous les contrôles passent."; else echo "→ Des contrôles échouent."; exit 1; fi
