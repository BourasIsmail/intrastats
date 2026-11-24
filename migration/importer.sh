#!/usr/bin/env bash
# Importe les exports de l'ancienne base (fichiers .sql) puis lance la migration.
# À exécuter DANS le conteneur MySQL (voir migration/README.md) :
#   docker compose exec db bash /migration/importer.sh [dossier_des_exports] [options de migrate.sh]
#
# Variables : MYSQL_ROOT_PASSWORD (déjà définie dans le conteneur db), OLD_DB (défaut entraide_old), NEW_DB (défaut entraide).
set -euo pipefail

DUMPS="${1:-/dumps}"
[[ $# -gt 0 ]] && shift
OLD="${OLD_DB:-entraide_old}"
NEW="${NEW_DB:-entraide}"
SRC="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

CRED=(-uroot)
[[ -n "${MYSQL_ROOT_PASSWORD:-}" ]] && CRED+=("-p${MYSQL_ROOT_PASSWORD}")
MYSQL=(mysql "${CRED[@]}" --default-character-set=utf8mb4)

shopt -s nullglob
FILES=("$DUMPS"/*.sql)
if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "Aucun fichier .sql dans $DUMPS" >&2
  exit 1
fi

echo "→ Base temporaire $OLD (recréée à chaque lancement)"
"${MYSQL[@]}" 2>/dev/null <<SQL
DROP DATABASE IF EXISTS \`$OLD\`;
CREATE DATABASE \`$OLD\` CHARACTER SET utf8mb4;
SQL

for f in "${FILES[@]}"; do
  echo "→ Import de $(basename "$f")"
  tr -d '\r' < "$f" | "${MYSQL[@]}" "$OLD" 2>&1 | grep -v "Using a password" || true
done

echo "→ Vérification des tables"
manquantes=()
for t in provinces regions_nv structures communes users locaux centres activites activite_centre personnel; do
  n=$("${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA='$OLD' AND TABLE_NAME='$t'" 2>/dev/null)
  if [[ "$n" == "0" ]]; then manquantes+=("$t"); else
    lignes=$("${MYSQL[@]}" -N -e "SELECT COUNT(*) FROM \`$OLD\`.\`$t\`" 2>/dev/null)
    printf '   %-16s %s lignes\n' "$t" "$lignes"
  fi
done
if [[ ${#manquantes[@]} -gt 0 ]]; then
  echo "Tables manquantes : ${manquantes[*]}. Exportez-les depuis phpMyAdmin (onglet Exporter), ajoutez-les au dossier $DUMPS et relancez." >&2
  exit 1
fi

# Copie des scripts avec fins de ligne Unix (un dépôt cloné sous Windows peut les avoir en CRLF).
WORK="$(mktemp -d)"
cp -r "$SRC"/. "$WORK"/
find "$WORK" -type f \( -name '*.sh' -o -name '*.sql' \) -exec sed -i 's/\r$//' {} +

echo "→ Migration $OLD ⇒ $NEW"
bash "$WORK/migrate.sh" "$OLD" "$NEW" --reset "$@" -- "${CRED[@]}"
