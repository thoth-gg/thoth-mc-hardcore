#!/usr/bin/env bash

set -u

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SERVER_DIR="$SCRIPT_DIR"
JAR_NAME="spigot-26.1.1.jar"
BACKUP_ROOT="$SERVER_DIR/backup"
RESET_REQUEST_FILE="$SERVER_DIR/plugins/thoth-mc-hardcore/reset-world.lock"

cd "$SERVER_DIR" || exit 1

while true; do
  java -Xmx4096M -Xms4096M -jar "./$JAR_NAME" nogui
  exit_code=$?

  if [[ -f "$RESET_REQUEST_FILE" ]]; then
    timestamp="$(date '+%Y%m%d-%H%M%S')"
    backup_dir="$BACKUP_ROOT/$timestamp"
    moved_any=false

    shopt -s nullglob
    world_dirs=(world*)
    shopt -u nullglob

    for dir in "${world_dirs[@]}"; do
      if [[ -d "$dir" ]]; then
        mkdir -p "$backup_dir"
        mv "$dir" "$backup_dir/"
        moved_any=true
      fi
    done

    rm -f "$RESET_REQUEST_FILE"
    rm -f "$SERVER_DIR/plugins/thoth-mc-hardcore/run-stats.yml"

    if [[ "$moved_any" == true ]]; then
      printf 'Moved world directories to %s\n' "$backup_dir"
    else
      printf 'Reset requested, but no world directories were found.\n'
    fi
  else
    printf 'No reset request file found. Keeping world directories as-is.\n'
  fi

  printf 'Server stopped with exit code %s. Restarting in 1 second...\n' "$exit_                                                                                                                 code"
  sleep 1
done
