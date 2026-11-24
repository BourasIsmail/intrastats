#!/usr/bin/env bash
# Migre l'ancienne base vers le nouveau schéma, puis affiche les contrôles.
#
# Usage :  ./migrate.sh ANCIENNE_BASE NOUVELLE_BASE [--reset] [-- options du client mysql]
#   ex. :  ./migrate.sh entraide_intranet_db entraide --reset -- -uroot -p
#
#   --reset   vide d'abord les tables du nouveau schéma (sauf l'historique Flyway). À n'utiliser que sur une base
#             de migration : les utilisateurs, centres et statistiques déjà présents sont supprimés.
#
# Sans --reset, le script refuse de s'exécuter si le nouveau schéma contient déjà des données.
set -euo pipefail

if [[ $# -lt 2 ]]; then
  sed -n '2,12p' "$0"
  exit 1
fi

OLD="$1"; NEW="$2"; shift 2
RESET=0
if [[ "${1:-}" == "--reset" ]]; then RESET=1; shift; fi
if [[ "${1:-}" == "--" ]]; then shift; fi
MYSQL=(mysql --default-character-set=utf8mb4 "$@")

for name in "$OLD" "$NEW"; do
  [[ "$name" =~ ^[A-Za-z0-9_]+$ ]] || { echo "Nom de base invalide : $name" >&2; exit 1; }
done

DIR="$(cd "$(dirname "$0")" && pwd)"
sub() { sed -e "s/__OLD__/$OLD/g" -e "s/__NEW__/$NEW/g" "$1"; }

nb=$("${MYSQL[@]}" -N -e "SELECT (SELECT COUNT(*) FROM $NEW.region) + (SELECT COUNT(*) FROM $NEW.centre) + (SELECT COUNT(*) FROM $NEW.utilisateur) + (SELECT COUNT(*) FROM $NEW.periode)")
if [[ "$nb" != "0" ]]; then
  if [[ $RESET -eq 1 ]]; then
    echo "→ Remise à zéro du nouveau schéma ($NEW)"
    "${MYSQL[@]}" -e "
      SET FOREIGN_KEY_CHECKS = 0;
      TRUNCATE $NEW.journal_audit; TRUNCATE $NEW.statistique; TRUNCATE $NEW.saisie_province; TRUNCATE $NEW.periode;
      TRUNCATE $NEW.salle; TRUNCATE $NEW.centre_activite; TRUNCATE $NEW.personnel; TRUNCATE $NEW.activite; TRUNCATE $NEW.activite_categorie;
      TRUNCATE $NEW.axe; TRUNCATE $NEW.programme; TRUNCATE $NEW.centre_equipement; TRUNCATE $NEW.centre_foncier;
      TRUNCATE $NEW.centre; TRUNCATE $NEW.utilisateur; TRUNCATE $NEW.commune; TRUNCATE $NEW.province; TRUNCATE $NEW.region;
      SET FOREIGN_KEY_CHECKS = 1;"
  else
    echo "Le nouveau schéma ($NEW) contient déjà des données ($nb lignes). Relancez avec --reset pour repartir de zéro." >&2
    exit 2
  fi
fi

echo "→ Import : $OLD ⇒ $NEW"
sub "$DIR/01_import.sql" | "${MYSQL[@]}"

echo "→ Contrôles"
sub "$DIR/02_controles.sql" | "${MYSQL[@]}" -t
