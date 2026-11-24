#!/usr/bin/env bash
# Exporte les données de la base migrée (nouveau schéma) dans un fichier SQL que l'application charge automatiquement
# au premier démarrage (voir donnees-initiales/LISEZMOI.md). Les périodes, statistiques et le journal ne sont pas exportés.
#
# À exécuter dans le conteneur MySQL, après importer.sh :
#   docker compose exec db bash /migration/exporter_donnees.sh [base] [fichier]
set -euo pipefail

NEW="${1:-${NEW_DB:-entraide}}"
OUT="${2:-/tmp/V6_1__donnees_initiales.sql}"

CRED=(-uroot)
[[ -n "${MYSQL_ROOT_PASSWORD:-}" ]] && CRED+=("-p${MYSQL_ROOT_PASSWORD}")

# Ordre imposé par les clés étrangères.
TABLES=(region province commune centre centre_foncier centre_equipement programme axe activite_categorie activite
        personnel centre_activite salle utilisateur)

{
  echo "-- Données initiales reprises de l'ancienne base. Généré par migration/exporter_donnees.sh le $(date +%F)."
  echo "-- Fichier contenant des données personnelles : ne pas le publier."
  mysqldump "${CRED[@]}" --no-create-info --skip-triggers --skip-add-locks --skip-disable-keys --skip-comments \
            --complete-insert --no-tablespaces --default-character-set=utf8mb4 --set-gtid-purged=OFF "$NEW" "${TABLES[@]}" 2> >(grep -v "Using a password" >&2)
} > "$OUT"

echo "Fichier créé : $OUT ($(du -h "$OUT" | cut -f1))"
for t in "${TABLES[@]}"; do
  printf '   %-18s %s lignes\n' "$t" "$(mysql "${CRED[@]}" -N -e "SELECT COUNT(*) FROM \`$NEW\`.\`$t\`" 2>/dev/null)"
done
