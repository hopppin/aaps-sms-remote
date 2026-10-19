#!/usr/bin/env bash
# To samo co CI: testy, lint (i opcjonalnie build). Ustawia JAVA_HOME i local.properties,
# jeśli ich brakuje (np. w świeżym worktree).
# Użycie: scripts/sprawdz.sh            -> testDebugUnitTest lintDebug
#         scripts/sprawdz.sh --pelne    -> + assembleDebug
#         scripts/sprawdz.sh <zadania>  -> dowolne zadania Gradle
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

if [ -z "${JAVA_HOME:-}" ] && ! command -v java >/dev/null 2>&1; then
  for jbr in "/c/Program Files/Android/Android Studio/jbr" \
             "/Applications/Android Studio.app/Contents/jbr/Contents/Home" \
             "/opt/android-studio/jbr"; do
    if [ -d "$jbr" ]; then export JAVA_HOME="$jbr"; break; fi
  done
fi

if [ ! -f local.properties ]; then
  sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
  if [ -z "$sdk" ] && [ -d "$HOME/AppData/Local/Android/Sdk" ]; then sdk="C:/Users/$(basename "$HOME")/AppData/Local/Android/Sdk"; fi
  if [ -z "$sdk" ] && [ -d "$HOME/Library/Android/sdk" ]; then sdk="$HOME/Library/Android/sdk"; fi
  if [ -z "$sdk" ] && [ -d "$HOME/Android/Sdk" ]; then sdk="$HOME/Android/Sdk"; fi
  if [ -n "$sdk" ]; then
    # local.properties to plik Properties: "\" jest znakiem ucieczki, a ":" separatorem –
    # zamieniamy ukośniki odwrotne na zwykłe i escapujemy dwukropek.
    printf 'sdk.dir=%s\n' "$(printf '%s' "$sdk" | sed -e 's#\\#/#g' -e 's/:/\\:/g')" > local.properties
    echo "Utworzono local.properties (sdk.dir=$sdk)"
  fi
fi

if [ "$#" -eq 0 ]; then
  set -- testDebugUnitTest lintDebug
elif [ "$1" = "--pelne" ]; then
  set -- testDebugUnitTest lintDebug assembleDebug
fi

./gradlew "$@" --console=plain

results=app/build/test-results/testDebugUnitTest
if compgen -G "$results/*.xml" >/dev/null; then
  cat "$results"/*.xml | grep -o 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' \
    | awk -F'"' '{t+=$2; s+=$4; f+=$6; e+=$8} END {printf "Testy: %d, pominięte: %d, porażki: %d, błędy: %d\n", t, s, f, e}'
fi
