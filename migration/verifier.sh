#!/usr/bin/env bash
# Vérifie que les données sont bien dans la base de l'application (après migration ou chargement des données initiales).
#   docker compose exec db bash /migration/verifier.sh [base]
# Les « attendus » correspondent aux exports fournis lors de l'essai ; avec d'autres exports, seuls les contrôles de cohérence comptent.
set -uo pipefail
DB="${1:-${NEW_DB:-entraide}}"
CRED=(-uroot)
[[ -n "${MYSQL_ROOT_PASSWORD:-}" ]] && CRED+=("-p${MYSQL_ROOT_PASSWORD}")
q() { mysql "${CRED[@]}" --default-character-set=utf8mb4 -N -e "$1" "$DB" 2>/dev/null; }

fail=0
compte() {   # compte table attendu
  local n; n=$(q "SELECT COUNT(*) FROM \`$1\`")
  if [[ -z "$n" ]]; then printf '  %-22s table introuvable\n' "$1"; fail=1; return; fi
  if [[ "$n" == "$2" ]]; then printf '  OK     %-22s %6s\n' "$1" "$n"; else printf '  ECART  %-22s %6s (attendu %s)\n' "$1" "$n" "$2"; fail=1; fi
}
zero() {     # zero "libellé" "requête qui doit renvoyer 0"
  local n; n=$(q "$2")
  if [[ "$n" == "0" ]]; then printf '  OK     %s\n' "$1"; else printf '  ECART  %s : %s\n' "$1" "${n:-erreur}"; fail=1; fi
}

echo "Base : $DB"
echo; echo "1) Nombre de lignes par table"
compte region 12; compte province 82; compte commune 2028
compte centre 5865; compte centre_foncier 5865; compte centre_equipement 5865
compte programme 21; compte axe 27; compte activite_categorie 35; compte activite 53
compte centre_activite 9173; compte salle 173; compte personnel 2809; compte utilisateur 168

echo; echo "2) Cohérence"
zero "provinces sans région"                       "SELECT COUNT(*) FROM province p LEFT JOIN region r ON r.id=p.region_id WHERE r.id IS NULL"
zero "provinces sans nom arabe"                    "SELECT COUNT(*) FROM province WHERE nom_ar IS NULL OR nom_ar=''"
zero "centres sans province"                       "SELECT COUNT(*) FROM centre c LEFT JOIN province p ON p.id=c.province_id WHERE p.id IS NULL"
zero "codes de centre en double"                   "SELECT COUNT(*) - COUNT(DISTINCT code) FROM centre"
zero "activités de centre sans centre ou activité" "SELECT COUNT(*) FROM centre_activite ca LEFT JOIN centre c ON c.id=ca.centre_id LEFT JOIN activite a ON a.id=ca.activite_id WHERE c.id IS NULL OR a.id IS NULL"
zero "agents sans province"                        "SELECT COUNT(*) FROM personnel WHERE province_id IS NULL"
zero "comptes PROVINCIAL sans province"            "SELECT COUNT(*) FROM utilisateur WHERE role='PROVINCIAL' AND province_id IS NULL"
zero "comptes REGIONAL sans région"                "SELECT COUNT(*) FROM utilisateur WHERE role='REGIONAL' AND region_id IS NULL"
zero "comptes sans mot de passe {mysql} ni bcrypt" "SELECT COUNT(*) FROM utilisateur WHERE mot_de_passe NOT LIKE '{mysql}%' AND mot_de_passe NOT LIKE '{bcrypt}%'"
zero "comptes ADMIN (au moins un attendu)"         "SELECT IF(COUNT(*)>0,0,1) FROM utilisateur WHERE role='ADMIN' AND actif=1"
zero "provinces sans aucun compte provincial"      "SELECT COUNT(*) FROM province WHERE id NOT IN (SELECT province_id FROM utilisateur WHERE role='PROVINCIAL' AND province_id IS NOT NULL)"

echo; echo "3) Répartition"
echo "  Comptes par rôle :";        q "SELECT CONCAT('    ', role, ' : ', COUNT(*), ' (actifs : ', SUM(actif), ')') FROM utilisateur GROUP BY role"
echo "  Centres par région (5 premières) :"; q "SELECT CONCAT('    ', r.nom_fr, ' : ', COUNT(*)) FROM centre c JOIN province p ON p.id=c.province_id JOIN region r ON r.id=p.region_id GROUP BY r.id ORDER BY COUNT(*) DESC LIMIT 5"
echo "  Activités ouvertes / cessées :"; q "SELECT CONCAT('    ouvertes : ', SUM(date_cessation IS NULL), ' - cessées : ', SUM(date_cessation IS NOT NULL)) FROM centre_activite"

echo; echo "4) Historique Flyway (rempli par l'application)"
q "SELECT CONCAT('  version ', version, ' - ', description, ' : ', IF(success,'OK','ECHEC')) FROM flyway_schema_history ORDER BY installed_rank" || true
if [[ -n "$(q "SELECT 1 FROM flyway_schema_history WHERE CAST(SUBSTRING_INDEX(version, '.', 1) AS UNSIGNED) >= 100 LIMIT 1")" ]]; then
  echo "  ATTENTION : des données d'EXEMPLE (versions 100 et plus, profil dev) sont présentes."
  echo "  Mettez SPRING_PROFILES_ACTIVE= (vide) dans .env, puis : docker compose down -v ; docker compose up --build"
  fail=1
fi

echo
if [[ $fail -eq 0 ]]; then echo "→ Tout est conforme."; else echo "→ Des écarts sont à examiner (voir ci-dessus)."; exit 1; fi
