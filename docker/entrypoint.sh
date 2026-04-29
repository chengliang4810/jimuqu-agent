#!/usr/bin/env bash
set -e

JIMUQU_HOME="${JIMUQU_HOME:-/app/runtime}"

if [ "$(id -u)" = "0" ]; then
    if [ -n "$JIMUQU_UID" ] && [ "$JIMUQU_UID" != "$(id -u jimuqu)" ]; then
        usermod -u "$JIMUQU_UID" jimuqu
    fi

    if [ -n "$JIMUQU_GID" ] && [ "$JIMUQU_GID" != "$(id -g jimuqu)" ]; then
        groupmod -o -g "$JIMUQU_GID" jimuqu 2>/dev/null || true
    fi

    mkdir -p "$JIMUQU_HOME"
    actual_uid="$(id -u jimuqu)"
    needs_chown=false
    if [ -n "$JIMUQU_UID" ] && [ "$JIMUQU_UID" != "10000" ]; then
        needs_chown=true
    elif [ "$(stat -c %u "$JIMUQU_HOME" 2>/dev/null)" != "$actual_uid" ]; then
        needs_chown=true
    fi

    if [ "$needs_chown" = true ]; then
        chown -R jimuqu:jimuqu "$JIMUQU_HOME" 2>/dev/null || \
            echo "Warning: failed to chown $JIMUQU_HOME; continuing"
    fi

    if [ -f "$JIMUQU_HOME/config.yml" ]; then
        chown jimuqu:jimuqu "$JIMUQU_HOME/config.yml" 2>/dev/null || true
        chmod 640 "$JIMUQU_HOME/config.yml" 2>/dev/null || true
    fi

    exec gosu jimuqu "$0" "$@"
fi

exec java -jar /app/jimuqu-agent.jar "$@"
