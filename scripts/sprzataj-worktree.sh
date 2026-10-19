#!/usr/bin/env bash
# Usuwa worktree agentów z .claude/worktrees/ i ich lokalne gałęzie.
# Worktree jest pomijany, gdy:
#   - ma niezacommitowane zmiany lub nowe pliki (ignorowane, np. build/, się nie liczą),
#   - jego gałąź ma otwarty PR,
#   - nie ma scalonego PR, a gałąź ma commity, których nie ma na zdalnym (agent pracuje?).
# Scalony PR (także squash, po usunięciu zdalnej gałęzi) pozwala usunąć worktree.
# --sila pomija wszystkie kontrole.
# Na Windows `git worktree remove` pada na długich ścieżkach w build/, a demon Gradle
# blokuje katalogi – dlatego najpierw `gradlew --stop`, potem rm -rf.
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"
sila=0; [ "${1:-}" = "--sila" ] && sila=1

[ -d .claude/worktrees ] || { echo "Brak .claude/worktrees – nic do sprzątania."; exit 0; }
git fetch -q --prune origin || true
./gradlew --stop -q >/dev/null 2>&1 || true

pr_state() { # stan PR dla gałęzi: OPEN / MERGED / CLOSED / pusty
  command -v gh >/dev/null 2>&1 || return 0
  gh pr list --head "$1" --state all --json state --jq '.[0].state // ""' 2>/dev/null || true
}

for w in .claude/worktrees/*/; do
  w=${w%/}
  [ -e "$w/.git" ] || { rm -rf "$w"; continue; }
  branch=$(git -C "$w" rev-parse --abbrev-ref HEAD 2>/dev/null || echo "")
  if [ "$sila" -eq 0 ]; then
    if [ -n "$(git -C "$w" status --porcelain --untracked-files=normal)" ]; then
      echo "POMINIĘTO $w: niezacommitowane zmiany lub nowe pliki"; continue
    fi
    state=$(pr_state "$branch")
    if [ "$state" = "OPEN" ]; then
      echo "POMINIĘTO $w: otwarty PR dla $branch"; continue
    fi
    if [ "$state" != "MERGED" ] && [ -n "$(git -C "$w" log --oneline HEAD --not --remotes 2>/dev/null | head -1)" ]; then
      echo "POMINIĘTO $w: commity, których nie ma na zdalnym, i brak scalonego PR"; continue
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
# Gałęzie pomocnicze tworzone przez Claude Code dla worktree (worktree-agent-*):
# usuwamy, gdy żaden worktree ich nie używa i nie mają nic ponad origin/main.
in_use=$(git worktree list --porcelain | sed -n 's#^branch refs/heads/##p')
for b in $(git branch --list 'worktree-agent-*' --format='%(refname:short)'); do
  printf '%s\n' "$in_use" | grep -qx "$b" && continue
  if git merge-base --is-ancestor "$b" origin/main 2>/dev/null; then
    git branch -D "$b" >/dev/null && echo "Usunięto gałąź pomocniczą $b"
  fi
done
rmdir .claude/worktrees 2>/dev/null || true
git worktree list
