#!/usr/bin/env bash
# Maps the English text the client draws from the cache. Read-only: never writes to the cache.
# Usage, from the repository root:
#   bash tools/translation/mapa_cliente/mapa.sh <cache dir> <output dir>
# Use the original cache (~/Documents/void-downloads/cache-antes-tarefas-pt) for the full English count,
# or data/cache to see only what the translation patch still leaves in English.
# Needs the cache and buffer module jars (CACHE_JAR, BUFFER_JAR; default cache/build/libs/cache-dev.jar and
# buffer/build/libs/buffer-dev.jar, built by ./gradlew build) and a server jar for the other dependencies
# (VOID_SERVER_JAR, default ~/Documents/void-downloads/run/void-server.jar).
set -euo pipefail

CACHE="${1:?cache dir}"
OUT="${2:?output dir}"
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
HERE="$ROOT/tools/translation/mapa_cliente"
CACHE_JAR="${CACHE_JAR:-$ROOT/cache/build/libs/cache-dev.jar}"
BUFFER_JAR="${BUFFER_JAR:-$ROOT/buffer/build/libs/buffer-dev.jar}"
SERVER_JAR="${VOID_SERVER_JAR:-$HOME/Documents/void-downloads/run/void-server.jar}"

find_jar() {
    find "$HOME/.gradle/caches/modules-2" -name "$1" 2>/dev/null | sort | tail -1
}
RS_CACHE_JAR="$(find_jar 'rs-cache-library-*.jar')"
KOTLIN_JAR="$(find_jar 'kotlin-stdlib-2.1.*.jar')"

for jar in "$CACHE_JAR" "$BUFFER_JAR" "$RS_CACHE_JAR" "$KOTLIN_JAR" "$SERVER_JAR"; do
    if [ ! -f "$jar" ]; then
        echo "Missing jar: '$jar'" >&2
        exit 1
    fi
done
# The module jars come first: the server jar is minimised and lacks some decoders
CP="$CACHE_JAR:$BUFFER_JAR:$RS_CACHE_JAR:$KOTLIN_JAR:$SERVER_JAR"

mkdir -p "$OUT/classes"
javac -cp "$CP" -d "$OUT/classes" "$HERE/Dump.java" "$HERE/Count.java"
java -cp "$OUT/classes:$CP" Dump "$CACHE" "$OUT" > "$OUT/dump.log"
java -cp "$OUT/classes:$CP" Count "$CACHE" > "$OUT/count.log"
# Keep only the counts, without the loader's coloured log lines
sed 's/\x1b\[[0-9;]*m//g' "$OUT/count.log" | grep -E '^[a-z_]+\.' > "$OUT/other_texts.txt"
python3 -I "$HERE/analyse.py" "$ROOT" "$OUT"
echo "Written to $OUT: per_interface.json, appendix.json, shared_scripts.json, other_texts.txt"
