#!/usr/bin/env bash
# Usuwa worktree agentów z .claude/worktrees/ i ich lokalne gałęzie.
# Pomija worktree z niezacommitowanymi zmianami albo commitami, których nie ma na żadnej
# zdalnej gałęzi (np. agent jeszcze pracuje) – chyba że podasz --sila.
# Na Windows `git worktree remove` pada na długich ścieżkach w build/, a demon Gradle
# blokuje katalogi – dlatego najpierw `gradlew --stop`, potem rm -rf.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
sila=0; [ "${1:-}" = "--sila" ] && sila=1

[ -d .claude/worktrees ] || { echo "Brak .claude/worktrees – nic do sprzątania."; exit 0; }
git fetch -q --prune origin || true
./gradlew --stop -q >/dev/null 2>&1 || true

for w in .claude/worktrees/*/; do
  w=${w%/}
  [ -e "$w/.git" ] || { rm -rf "$w"; continue; }
  branch=$(git -C "$w" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
  if [ "$sila" -eq 0 ]; then
    if [ -n "$(git -C "$w" status --porcelain --untracked-files=no)" ]; then
      echo "POMINIĘTO $w: niezacommitowane zmiany"; continue
    fi
    if [ -n "$(git -C "$w" log --oneline HEAD --not --remotes 2>/dev/null | head -1)" ]; then
      echo "POMINIĘTO $w: commity, których nie ma na zdalnym (agent pracuje?)"; continue
    fi
  fi
  rm -rf "$w"
  echo "Usunięto $w"
  git worktree prune
  if [ -n "$branch" ] && [ "$branch" != "HEAD" ] && [ "$branch" != "main" ]; then
    git branch -D "$branch" >/dev/null 2>&1 && echo "  i gałąź $branch" || true
  fi
done
git worktree prune
rmdir .claude/worktrees 2>/dev/null || true
git worktree list
