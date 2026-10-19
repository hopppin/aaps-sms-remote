---
name: aktualizuj-stan
description: Aktualizuje docs/stan-prac.md (zrobione, w toku, następne kroki, ustalenia, otwarte pytania) na podstawie GitHuba i specyfikacji. Użyj na końcu sesji, po scaleniu PR-ów albo gdy użytkownik prosi o podsumowanie pracy.
---

# Aktualizacja stanu prac

1. Zbierz fakty (nie z pamięci):

   ```bash
   gh pr list --state all --limit 30 --json number,title,state,mergedAt --jq '.[] | "#\(.number) \(.state) \(.title)"'
   gh issue list --state all --limit 50 --json number,title,state --jq '.[] | "#\(.number) \(.state) \(.title)"'
   grep -n '^|' docs/specs/README.md
   ```

2. Zaktualizuj `docs/stan-prac.md`:
   - datę „Ostatnia aktualizacja”,
   - „Zrobione” – scalone PR-y pogrupowane po specyfikacji/pakiecie,
   - „W toku” – otwarte issues z zależnościami,
   - „Następne kroki” – zgodnie z kolejnością z `docs/plan.md` i zależnościami,
   - „Ustalenia z użytkownikiem” – tylko decyzje, których nie ma jeszcze w spec/AGENTS.md albo które są kluczowe dla przyszłych spec,
   - „Otwarte” – pytania bez odpowiedzi; usuń rozstrzygnięte.
3. Nie dopisuj prawdziwych danych użytkownika (imion, numerów, PIN-ów, sekretów).
4. Zmiana idzie zwykłym PR (gałąź `docs/stan-<data>`), jak każda inna – albo razem z PR, który zamyka etap.
