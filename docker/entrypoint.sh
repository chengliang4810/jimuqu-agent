#!/usr/bin/env bash
set -e

RUNTIME_HOME="/app/runtime"

if [ "$(id -u)" = "0" ]; then
    mkdir -p "$RUNTIME_HOME"
    chown -R solonclaw:solonclaw "$RUNTIME_HOME" 2>/dev/null || \
        echo "Warning: failed to chown $RUNTIME_HOME; continuing"

    if [ -f "$RUNTIME_HOME/config.yml" ]; then
        chown solonclaw:solonclaw "$RUNTIME_HOME/config.yml" 2>/dev/null || true
        chmod 640 "$RUNTIME_HOME/config.yml" 2>/dev/null || true
    fi

    write_probe="$RUNTIME_HOME/.solonclaw-write-test"
    if ! gosu solonclaw sh -c 'touch "$1" && rm -f "$1"' sh "$write_probe" 2>/dev/null; then
        echo "Error: $RUNTIME_HOME is not writable by user solonclaw."
        echo "Fix host permissions for the bind-mounted runtime directory."
        exit 1
    fi

    exec gosu solonclaw "$0" "$@"
fi

exec java -jar /app/solon-claw.jar "$@"
